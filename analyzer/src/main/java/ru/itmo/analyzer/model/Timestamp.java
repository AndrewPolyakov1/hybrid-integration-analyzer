package ru.itmo.analyzer.model;

import java.time.LocalDateTime;

/**
 * Отметка о времени вызова
 *
 * @param value время
 */
public record Timestamp(LocalDateTime value) {
}
