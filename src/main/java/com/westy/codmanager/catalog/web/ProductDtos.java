package com.westy.codmanager.catalog.web;

import com.westy.codmanager.catalog.domain.Product;
import com.westy.codmanager.catalog.domain.ProductVariant;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 64) String sku,
            @NotNull @DecimalMin("0.00") BigDecimal basePrice,
            @DecimalMin("0.00") BigDecimal costPrice) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0.00") BigDecimal basePrice,
            @DecimalMin("0.00") BigDecimal costPrice,
            boolean active) {
    }

    public record CreateVariantRequest(
            @Size(max = 32) String size,
            @Size(max = 32) String color,
            @NotBlank @Size(max = 64) String sku,
            @Min(0) int stockQty) {
    }

    public record VariantResponse(String id, String size, String color, String sku, int stockQty) {

        public static VariantResponse from(ProductVariant variant) {
            return new VariantResponse(variant.getId().toString(), variant.getSize(),
                    variant.getColor(), variant.getSku(), variant.getStockQty());
        }
    }

    public record ProductResponse(
            String id,
            String name,
            String sku,
            BigDecimal basePrice,
            BigDecimal costPrice,
            BigDecimal unitMargin,
            boolean active,
            List<VariantResponse> variants) {

        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.getId().toString(),
                    product.getName(),
                    product.getSku(),
                    product.getBasePrice(),
                    product.getCostPrice(),
                    product.unitMargin(),
                    product.isActive(),
                    product.getVariants().stream().map(VariantResponse::from).toList());
        }
    }
}
