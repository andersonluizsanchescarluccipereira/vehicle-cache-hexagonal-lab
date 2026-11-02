package br.com.lab.vehiclecache.application.port.out;

import br.com.lab.vehiclecache.domain.model.VehicleChangedEvent;

public interface EventOutboxPort {
    void append(VehicleChangedEvent event);
}
