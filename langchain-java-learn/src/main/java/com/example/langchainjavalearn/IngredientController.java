package com.example.langchainjavalearn;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class IngredientController {

    private IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @PostMapping("/ingredient")
    public ResponseEntity<String> getIngredientExplaination(@RequestBody String ingredient) {
        return ResponseEntity.ok(ingredientService.explainIngredient(ingredient));
    }
}
