package br.com.lab.vehiclecache.adapter.out.events;

import br.com.lab.vehiclecache.application.port.out.AfterCommitEventPort;
import br.com.lab.vehiclecache.domain.model.VehicleCacheChange;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringAfterCommitPublisher implements AfterCommitEventPort {
    private final ApplicationEventPublisher publisher;
    SpringAfterCommitPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @Override public void publish(VehicleCacheChange change) { publisher.publishEvent(change); }
}
