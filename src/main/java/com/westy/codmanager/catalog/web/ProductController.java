package com.westy.codmanager.catalog.web;

import com.westy.codmanager.catalog.service.ProductService;
import com.westy.codmanager.catalog.web.ProductDtos.CreateProductRequest;
import com.westy.codmanager.catalog.web.ProductDtos.CreateVariantRequest;
import com.westy.codmanager.catalog.web.ProductDtos.ProductResponse;
import com.westy.codmanager.catalog.web.ProductDtos.UpdateProductRequest;
import com.westy.codmanager.catalog.web.ProductDtos.VariantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalog")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List your products")
    public Page<ProductResponse> list(@AuthenticationPrincipal String ownerId,
                                      @PageableDefault(size = 20) Pageable pageable) {
        return service.list(UUID.fromString(ownerId), pageable).map(ProductResponse::from);
    }

    @PostMapping
    @Operation(summary = "Create a product")
    public ResponseEntity<ProductResponse> create(@AuthenticationPrincipal String ownerId,
                                                  @Valid @RequestBody CreateProductRequest request) {
        ProductResponse body = ProductResponse.from(
                service.create(UUID.fromString(ownerId), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one product with its variants")
    public ProductResponse get(@AuthenticationPrincipal String ownerId, @PathVariable UUID id) {
        return ProductResponse.from(service.get(UUID.fromString(ownerId), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ProductResponse update(@AuthenticationPrincipal String ownerId,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(service.update(UUID.fromString(ownerId), id, request));
    }

    @PostMapping("/{id}/variants")
    @Operation(summary = "Add a variant to a product")
    public ResponseEntity<VariantResponse> addVariant(@AuthenticationPrincipal String ownerId,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody CreateVariantRequest request) {
        VariantResponse body = VariantResponse.from(
                service.addVariant(UUID.fromString(ownerId), id, request));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product and its variants")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String ownerId,
                                       @PathVariable UUID id) {
        service.delete(UUID.fromString(ownerId), id);
        return ResponseEntity.noContent().build();
    }
}
