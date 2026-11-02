package br.com.lab.vehiclecache.adapter.out.events;

import br.com.lab.vehiclecache.application.port.out.VehicleCachePort;
import br.com.lab.vehiclecache.domain.model.VehicleCacheChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class AfterCommitCacheSynchronizer {
    private static final Logger log = LoggerFactory.getLogger(AfterCommitCacheSynchronizer.class);
    private final VehicleCachePort cache;
    AfterCommitCacheSynchronizer(VehicleCachePort cache) { this.cache = cache; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void synchronize(VehicleCacheChange change) {
        try {
            if (change.vehicle() == null) cache.evict(change.id());
            else cache.put(change.vehicle());
        } catch (RuntimeException error) {
            // Falha de cache não desfaz transação já persistida no banco.
            log.warn("Sincronização de cache pós-commit falhou para {}: {}", change.id(), error.toString());
        }
    }
}
