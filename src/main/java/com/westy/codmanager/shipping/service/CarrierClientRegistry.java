package com.westy.codmanager.shipping.service;

import com.westy.codmanager.common.exception.BusinessRuleException;
import com.westy.codmanager.geo.domain.Carrier;
import com.westy.codmanager.shipping.integration.CarrierClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the right client for a carrier.
 *
 * Spring injects every CarrierClient bean on the classpath, so a new courier
 * becomes available by writing one class. Nothing here changes.
 */
@Component
public class CarrierClientRegistry {

    private final Map<Carrier, CarrierClient> clients = new EnumMap<>(Carrier.class);

    public CarrierClientRegistry(List<CarrierClient> available) {
        available.forEach(client -> clients.put(client.carrier(), client));
    }

    public CarrierClient forCarrier(Carrier carrier) {
        CarrierClient client = clients.get(carrier);

        if (client == null) {
            throw new BusinessRuleException("CARRIER_UNSUPPORTED",
                    carrier + " is not integrated yet");
        }

        return client;
    }

    public boolean supports(Carrier carrier) {
        return clients.containsKey(carrier);
    }
}
