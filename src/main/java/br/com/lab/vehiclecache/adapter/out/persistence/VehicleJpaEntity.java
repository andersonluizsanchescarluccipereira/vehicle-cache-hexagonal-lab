package br.com.lab.vehiclecache.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "vehicles")
class VehicleJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String marca;
    @Column(nullable = false) private String modelo;
    @Column(nullable = false) private int ano;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal valor;
    @Version @Column(nullable = false) private Long version;

    protected VehicleJpaEntity() {}
    VehicleJpaEntity(String marca, String modelo, int ano, BigDecimal valor) {
        this.marca = marca; this.modelo = modelo; this.ano = ano; this.valor = valor;
    }
    Long id() { return id; }
    String marca() { return marca; }
    String modelo() { return modelo; }
    int ano() { return ano; }
    BigDecimal valor() { return valor; }
    long version() { return version == null ? 0 : version; }
    void update(String marca, String modelo, int ano, BigDecimal valor) {
        this.marca = marca; this.modelo = modelo; this.ano = ano; this.valor = valor;
    }
}
