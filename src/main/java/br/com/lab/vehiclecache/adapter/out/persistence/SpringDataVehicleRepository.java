package br.com.lab.vehiclecache.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataVehicleRepository extends JpaRepository<VehicleJpaEntity, Long> {}
