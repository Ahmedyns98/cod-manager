package com.westy.codmanager.catalog.service;

import com.westy.codmanager.catalog.domain.Product;
import com.westy.codmanager.catalog.domain.ProductVariant;
import com.westy.codmanager.catalog.repository.ProductRepository;
import com.westy.codmanager.catalog.repository.ProductVariantRepository;
import com.westy.codmanager.catalog.web.ProductDtos.CreateProductRequest;
import com.westy.codmanager.catalog.web.ProductDtos.CreateVariantRequest;
import com.westy.codmanager.catalog.web.ProductDtos.UpdateProductRequest;
import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.common.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Catalog management for one seller.
 *
 * Every read and write is scoped by owner id at the query level rather than
 * checked after loading, so there is no code path where a seller can reach
 * another seller's catalog by guessing an identifier.
 *
 * Product SKUs are unique per seller; variant SKUs are unique globally, because
 * they end up printed on labels and courier manifests where the seller is not
 * part of the identifier.
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public ProductService(ProductRepository products, ProductVariantRepository variants) {
        this.products = products;
        this.variants = variants;
    }

    @Transactional
    public Product create(UUID ownerId, CreateProductRequest request) {
        String sku = request.sku().trim();

        if (products.existsByOwnerIdAndSku(ownerId, sku)) {
            throw new BusinessRuleException("SKU_TAKEN",
                    "You already have a product with SKU " + sku);
        }

        return products.save(new Product(ownerId, request.name().trim(), sku,
                request.basePrice(), request.costPrice()));
    }

    @Transactional(readOnly = true)
    public Page<Product> list(UUID ownerId, Pageable pageable) {
        return products.findByOwnerId(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public Product get(UUID ownerId, UUID productId) {
        return products.findByIdAndOwnerId(productId, ownerId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
    }

    @Transactional
    public Product update(UUID ownerId, UUID productId, UpdateProductRequest request) {
        Product product = get(ownerId, productId);
        product.update(request.name().trim(), request.basePrice(),
                request.costPrice(), request.active());

        return product;
    }

    @Transactional
    public ProductVariant addVariant(UUID ownerId, UUID productId, CreateVariantRequest request) {
        Product product = get(ownerId, productId);
        String sku = request.sku().trim();

        /*
         * Variant SKUs are unique across the whole table, not per seller, because
         * they end up on physical labels and courier manifests.
         */
        if (variants.existsBySku(sku)) {
            throw new BusinessRuleException("SKU_TAKEN", "Variant SKU " + sku + " is already used");
        }

        ProductVariant variant = product.addVariant(
                request.size(), request.color(), sku, request.stockQty());

        products.save(product);

        return variant;
    }

    @Transactional
    public void delete(UUID ownerId, UUID productId) {
        products.delete(get(ownerId, productId));
    }
}
