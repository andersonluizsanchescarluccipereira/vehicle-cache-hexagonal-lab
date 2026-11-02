package br.com.lab.vehiclecache.application.port.out;

import br.com.lab.vehiclecache.domain.model.VehicleCacheChange;

public interface AfterCommitEventPort {
    void publish(VehicleCacheChange change);
}
