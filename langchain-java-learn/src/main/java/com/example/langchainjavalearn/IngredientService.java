package com.example.langchainjavalearn;

import org.springframework.stereotype.Service;

import dev.langchain4j.model.bedrock.BedrockMistralAiChatModel;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Service
public class IngredientService {

    private final BedrockMistralAiChatModel model;
    private final String prompt = "<s>[INST] </SYS>You are a renowned expert in diet and nutrition. You will be provided with a name of an ingredient from any food product, you must classify this ingredient in terms of how healthy it is to consume on a scale of 1 - 10.</SYS> %s [/INST]\"}";

    public IngredientService() {
        AwsCredentialsProvider awsCredentials = EnvironmentVariableCredentialsProvider.create();
        model = BedrockMistralAiChatModel
                .builder()
                .credentialsProvider(awsCredentials)
                .model(BedrockMistralAiChatModel.Types.Mistral7bInstructV0_2.getValue())
                .region(Region.AP_SOUTHEAST_2)
                .build();
    }

    public String explainIngredient(String ingredient) {
        return model.generate(String.format(prompt, ingredient));
    }

}
