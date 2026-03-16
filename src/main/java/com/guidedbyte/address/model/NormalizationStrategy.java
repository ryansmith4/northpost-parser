package com.guidedbyte.address.model;

/**
 * Strategy for normalizing parsed address components to canonical forms.
 *
 * <p>The parser preserves input forms by default (e.g., if the input says "BLVD", the output says "BLVD"). Use a
 * normalization strategy to convert components to a consistent canonical form.
 *
 * <p>Usage:
 *
 * <pre>
 *   var result = parser.parseAddress(address);
 *   var normalized = result.components().normalize(NormalizationStrategy.FULL_FORM);
 *   normalized.streetType(); // "BOULEVARD" instead of "BLVD"
 * </pre>
 */
public enum NormalizationStrategy {
    /** Normalize to full word forms (e.g., STREET, AVENUE, BOULEVARD, NORTH, EAST) */
    FULL_FORM,

    /** Normalize to Canada Post abbreviated forms (e.g., ST, AVE, BLVD, N, E) */
    ABBREVIATED
}
