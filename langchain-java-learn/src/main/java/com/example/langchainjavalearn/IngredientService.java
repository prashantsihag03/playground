package com.example.langchainjavalearn;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import com.azure.ai.vision.imageanalysis.ImageAnalysisClient;
import com.azure.ai.vision.imageanalysis.ImageAnalysisClientBuilder;
import com.azure.ai.vision.imageanalysis.models.DetectedTextLine;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisOptions;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisResult;
import com.azure.ai.vision.imageanalysis.models.ImagePoint;
import com.azure.ai.vision.imageanalysis.models.VisualFeatures;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.BinaryData;
import com.example.langchainjavalearn.DTOs.IngredientExplanationResultDTO;
import com.example.langchainjavalearn.DTOs.IngredientHealthDataDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
// import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
// import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
// import software.amazon.awssdk.regions.Region;

@Service
public class IngredientService {

  // private final BedrockMistralAiChatModel model;
  private final ChatLanguageModel openAiModel;
  private final ImageAnalysisClient client;

  private final String AZ_VISION_ENDPOINT;
  private final String AZ_VISION_KEY;
  private final String OPEN_AI_API_KEY;

  private final String prompt;

  public static boolean isValidJSON(String jsonString) {
    try {
      new JSONObject(jsonString);
    } catch (Exception ex) {
      try {
        new JSONArray(jsonString);
      } catch (Exception ex1) {
        return false;
      }
    }
    return true;
  }

  public IngredientService(Environment environment) throws IOException {
    OPEN_AI_API_KEY = environment.getRequiredProperty("openai.api.key", String.class);
    AZ_VISION_KEY = environment.getRequiredProperty("azure.vision.api.key", String.class);
    AZ_VISION_ENDPOINT = environment.getRequiredProperty("azure.vision.api.endpoint", String.class);

    prompt = new String(Files.readAllBytes(ResourceUtils.getFile("classpath:prompt.txt").toPath()));

    // AwsCredentialsProvider awsCredentials =
    // EnvironmentVariableCredentialsProvider.create();

    // model = BedrockMistralAiChatModel
    // .builder()
    // .credentialsProvider(awsCredentials)
    // .model(BedrockMistralAiChatModel.Types.Mistral7bInstructV0_2.getValue())
    // .region(Region.AP_SOUTHEAST_2)
    // .build();

    openAiModel = OpenAiChatModel.builder()
        .apiKey(OPEN_AI_API_KEY)
        .modelName(OpenAiChatModelName.GPT_4_O)
        .build();

    // Create a synchronous Image Analysis client.
    client = new ImageAnalysisClientBuilder()
        .endpoint(
            AZ_VISION_ENDPOINT)
        .credential(new KeyCredential(
            AZ_VISION_KEY))
        .buildClient();

  }

  public IngredientExplanationResultDTO explainIngredient(MultipartFile image)
      throws IOException {

    @SuppressWarnings("null")
    UserMessage userMessage = UserMessage.from(
        TextContent.from(prompt),
        ImageContent.from(
            Base64.getEncoder().encodeToString(image.getBytes()),
            MimeTypeUtils.parseMimeType(
                image.getContentType())
                .toString(),
            DetailLevel.AUTO));

    String result = null;
    try {
      result = openAiModel.generate(userMessage).content().text();
    } catch (Exception exception) {
      System.out.printf("Exception occurred while generating response from Open AI: {}", exception);
      throw exception;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    List<IngredientHealthDataDTO> ingredientHealthDataDTOs = Collections.emptyList();
    try {
      ingredientHealthDataDTOs = objectMapper.readValue(result,
          new TypeReference<List<IngredientHealthDataDTO>>() {

          });
    } catch (Exception e) {
      e.printStackTrace();
    }

    Map<String, IngredientHealthDataDTO> allIngredients = ingredientHealthDataDTOs.stream()
        .collect(Collectors.toMap(IngredientHealthDataDTO::getIngredient, Function.identity()));

    List<String> uniqueWords = allIngredients.keySet().stream()
        .flatMap(s -> Arrays.stream(s.split("\\s+")))
        .distinct()
        .map(t -> t.trim().toLowerCase())
        .collect(Collectors.toList());

    // This is a synchronous (blocking) call.
    ImageAnalysisResult result1 = client.analyze(
        BinaryData.fromBytes(
            image.getBytes()),
        Arrays.asList(VisualFeatures.READ),
        new ImageAnalysisOptions());

    IngredientExplanationResultDTO combinedResult = new IngredientExplanationResultDTO();
    combinedResult.setIngredientHealthData(allIngredients);

    Map<String, List<List<ImagePoint>>> boundingPoplygonsByWords = new HashMap<>();
    List<String> detectedWords = new ArrayList<>();

    for (DetectedTextLine line : result1.getRead().getBlocks().get(0).getLines()) {
      line.getWords().forEach(word -> {
        detectedWords.add(word.getText().trim().toLowerCase());
        if (uniqueWords.contains(word.getText().trim().toLowerCase())) {
          List<List<ImagePoint>> currBoundingPolygons = boundingPoplygonsByWords
              .getOrDefault(word.getText().trim().toLowerCase(),
                  new ArrayList<>());
          currBoundingPolygons.add(word.getBoundingPolygon());
          boundingPoplygonsByWords.put(word.getText(), currBoundingPolygons);
        }
      });
    }

    combinedResult.setIngredientWordsboundingPolygons(boundingPoplygonsByWords);
    combinedResult.setUniqueWords(uniqueWords);
    combinedResult.setDetectedWords(detectedWords);
    return combinedResult;
  }

}
