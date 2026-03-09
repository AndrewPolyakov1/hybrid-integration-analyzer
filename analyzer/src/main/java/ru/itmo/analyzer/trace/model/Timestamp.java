package ru.itmo.analyzer.trace.model;

import java.time.LocalDateTime;

/**
 * Отметка о времени вызова
 *
 * @param value время
 */
public record Timestamp(LocalDateTime value) {
}
