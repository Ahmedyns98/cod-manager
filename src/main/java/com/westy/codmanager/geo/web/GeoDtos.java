package com.westy.codmanager.geo.web;

import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.geo.domain.Commune;
import com.westy.codmanager.geo.domain.DeliveryFee;
import com.westy.codmanager.geo.domain.Wilaya;

import java.math.BigDecimal;

public final class GeoDtos {

    private GeoDtos() {
    }

    public record WilayaResponse(Short code, String nameFr, String nameAr, boolean south) {

        public static WilayaResponse from(Wilaya wilaya) {
            return new WilayaResponse(wilaya.getCode(), wilaya.getNameFr(),
                    wilaya.getNameAr(), wilaya.isSouth());
        }
    }

    public record CommuneResponse(Long id, String name, boolean hasStopdesk) {

        public static CommuneResponse from(Commune commune) {
            return new CommuneResponse(commune.getId(), commune.getName(), commune.hasStopdesk());
        }
    }

    public record DeliveryFeeResponse(
            Carrier carrier,
            Short wilayaCode,
            BigDecimal homePrice,
            BigDecimal stopdeskPrice) {

        public static DeliveryFeeResponse from(DeliveryFee fee) {
            return new DeliveryFeeResponse(fee.getCarrier(), fee.getWilaya().getCode(),
                    fee.getHomePrice(), fee.getStopdeskPrice());
        }
    }
}
