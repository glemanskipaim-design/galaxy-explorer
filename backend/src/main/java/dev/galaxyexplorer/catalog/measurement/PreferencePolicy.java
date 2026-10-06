package dev.galaxyexplorer.catalog.measurement;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Politica de conflito entre fontes (secao 7 do doc da Fase 0). Pura.
 *
 * <p>Agrupa por {@code (propertyCode, qualifier)} e escolhe exatamente um por
 * grupo, nesta ordem:
 * <ol>
 *   <li>menor {@code rank} aplicavel — se ha regras especificas da categoria do
 *       objeto para a propriedade, valem so elas; senao, as de categoria NULL.
 *       Fonte sem regra fica depois das que tem regra;</li>
 *   <li>menor incerteza relativa {@code max(errMinus, errPlus) / |valueNum|} —
 *       quem nao tem incerteza perde para quem tem;</li>
 *   <li>{@code retrievedAt} mais recente;</li>
 *   <li>menor {@code id} (so para ser deterministico).</li>
 * </ol>
 */
public final class PreferencePolicy {

    private PreferencePolicy() {
    }

    /** @param categoryId categoria do objeto; pode ser {@code null} */
    public static Set<Long> choosePreferred(List<PreferenceCandidate> candidates, List<PriorityRule> rules,
                                            Integer categoryId) {
        Map<List<String>, List<PreferenceCandidate>> groups = candidates.stream()
                .collect(Collectors.groupingBy(c -> List.of(c.propertyCode(), c.qualifier()), LinkedHashMap::new,
                        Collectors.toList()));
        Set<Long> preferred = new HashSet<>();
        for (Map.Entry<List<String>, List<PreferenceCandidate>> group : groups.entrySet()) {
            Map<Short, Short> ranks = ranksFor(group.getKey().get(0), rules, categoryId);
            Comparator<PreferenceCandidate> order = Comparator
                    .comparingInt((PreferenceCandidate c) -> ranks.getOrDefault(c.sourceId(), Short.MAX_VALUE))
                    .thenComparingDouble(PreferencePolicy::relativeUncertainty)
                    .thenComparing(PreferenceCandidate::retrievedAt,
                            Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                    .thenComparingLong(PreferenceCandidate::id);
            group.getValue().stream().min(order).ifPresent(c -> preferred.add(c.id()));
        }
        return preferred;
    }

    /** sourceId -> rank, usando as regras da categoria se houver, senao as genericas. */
    private static Map<Short, Short> ranksFor(String propertyCode, List<PriorityRule> rules, Integer categoryId) {
        List<PriorityRule> forProperty = rules.stream().filter(r -> r.propertyCode().equals(propertyCode)).toList();
        List<PriorityRule> specific = categoryId == null
                ? List.of()
                : forProperty.stream().filter(r -> Objects.equals(r.categoryId(), categoryId)).toList();
        List<PriorityRule> applicable = !specific.isEmpty()
                ? specific
                : forProperty.stream().filter(r -> r.categoryId() == null).toList();
        return applicable.stream().collect(Collectors.toMap(PriorityRule::sourceId, PriorityRule::rank,
                (a, b) -> (short) Math.min(a, b)));
    }

    /** Sem valor numerico, sem erro, ou valor zero -> infinito (perde para quem tem incerteza). */
    static double relativeUncertainty(PreferenceCandidate c) {
        if (c.valueNum() == null || c.valueNum() == 0.0 || (c.errMinus() == null && c.errPlus() == null)) {
            return Double.POSITIVE_INFINITY;
        }
        double err = Math.max(c.errMinus() == null ? 0.0 : c.errMinus(), c.errPlus() == null ? 0.0 : c.errPlus());
        return err / Math.abs(c.valueNum());
    }
}
