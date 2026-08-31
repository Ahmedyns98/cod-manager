package com.westy.codmanager.geo.web;

import com.westy.codmanager.common.exception.NotFoundException;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.geo.repository.CommuneRepository;
import com.westy.codmanager.geo.repository.DeliveryFeeRepository;
import com.westy.codmanager.geo.repository.WilayaRepository;
import com.westy.codmanager.geo.web.GeoDtos.CommuneResponse;
import com.westy.codmanager.geo.web.GeoDtos.DeliveryFeeResponse;
import com.westy.codmanager.geo.web.GeoDtos.WilayaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only reference data. It changes through migrations, never through the
 * API, so there is no write side here.
 */
@RestController
@RequestMapping("/api/v1/geo")
@Tag(name = "Geography")
public class GeoController {

    private final WilayaRepository wilayas;
    private final CommuneRepository communes;
    private final DeliveryFeeRepository fees;

    public GeoController(WilayaRepository wilayas, CommuneRepository communes,
                         DeliveryFeeRepository fees) {
        this.wilayas = wilayas;
        this.communes = communes;
        this.fees = fees;
    }

    @GetMapping("/wilayas")
    @Operation(summary = "List the 58 wilayas")
    public List<WilayaResponse> listWilayas() {
        return wilayas.findAll(Sort.by("code")).stream()
                .map(WilayaResponse::from)
                .toList();
    }

    @GetMapping("/wilayas/{code}/communes")
    @Operation(summary = "List the communes of one wilaya")
    public List<CommuneResponse> listCommunes(@PathVariable Short code) {
        if (!wilayas.existsById(code)) {
            throw new NotFoundException("Wilaya", code);
        }

        return communes.findByWilayaCodeOrderByNameAsc(code).stream()
                .map(CommuneResponse::from)
                .toList();
    }

    @GetMapping("/delivery-fees")
    @Operation(summary = "Delivery pricing for a carrier, optionally for one wilaya")
    public List<DeliveryFeeResponse> deliveryFees(@RequestParam Carrier carrier,
                                                  @RequestParam(required = false) Short wilaya) {
        if (wilaya == null) {
            return fees.findByCarrierOrderByWilayaCodeAsc(carrier).stream()
                    .map(DeliveryFeeResponse::from)
                    .toList();
        }

        return fees.findByCarrierAndWilayaCode(carrier, wilaya)
                .map(DeliveryFeeResponse::from)
                .map(List::of)
                .orElseThrow(() -> new NotFoundException(
                        "Delivery fee", carrier + "/" + wilaya));
    }
}
