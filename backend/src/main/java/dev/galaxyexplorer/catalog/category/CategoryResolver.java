package dev.galaxyexplorer.catalog.category;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decide a categoria "nossa" a partir do tipo bruto da fonte (+ morfologia).
 * Puro: sem banco e sem rede (as regras chegam como argumento). O unico estado
 * e o cache de {@link Pattern} compilado, por texto da regex.
 */
@Component
public class CategoryResolver {

    private static final Logger log = LoggerFactory.getLogger(CategoryResolver.class);

    private final Map<String, Optional<Pattern>> patterns = new ConcurrentHashMap<>();

    /**
     * Filtra regras com {@code sourceType} exato; mantem as de padrao vazio ou
     * cuja regex da {@code find()} no {@code morphType} (com morfologia nula,
     * so as vazias). Maior {@code priority} vence; empate -> menor {@code id}.
     * Sem regra aplicavel -> {@code fallbackOther}. Regex invalida e ignorada
     * (log WARN), nunca derruba a ingestao.
     *
     * @param morphType pode ser {@code null}
     */
    public CategoryDecision resolve(String sourceCode, String sourceType, String morphType,
                                    List<TypeMappingRule> rules, CategoryRow fallbackOther) {
        String morph = morphType == null ? null : morphType.trim();
        if (morph != null && morph.isEmpty()) {
            morph = null;
        }
        final String m = morph;
        Optional<TypeMappingRule> best = rules.stream()
                .filter(r -> r.sourceType().equals(sourceType))
                .filter(r -> matches(r, m))
                .min(Comparator.comparingInt((TypeMappingRule r) -> -r.priority()).thenComparingInt(TypeMappingRule::id));

        String typePart = sourceCode + " otype=" + sourceType + (m == null ? "" : " + morph_type=" + m);
        if (best.isEmpty()) {
            return new CategoryDecision(fallbackOther.id(), fallbackOther.code(),
                    sourceCode + " otype=" + sourceType + " sem regra de mapeamento");
        }
        TypeMappingRule rule = best.get();
        String rulePart = rule.morphPattern().isEmpty()
                ? "regra generica do otype"
                : "regra de morfologia '" + rule.morphPattern() + "'";
        return new CategoryDecision(rule.categoryId(), rule.categoryCode(),
                typePart + " -> " + rule.categoryCode() + " (" + rulePart + ", prioridade " + rule.priority() + ")");
    }

    private boolean matches(TypeMappingRule rule, String morph) {
        if (rule.morphPattern().isEmpty()) {
            return true;
        }
        if (morph == null) {
            return false;
        }
        return compiled(rule).map(p -> p.matcher(morph).find()).orElse(false);
    }

    private Optional<Pattern> compiled(TypeMappingRule rule) {
        return patterns.computeIfAbsent(rule.morphPattern(), regex -> {
            try {
                return Optional.of(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                log.warn("Regra source_type_mapping id={} com regex invalida '{}' ignorada: {}", rule.id(), regex,
                        e.getDescription());
                return Optional.empty();
            }
        });
    }
}
