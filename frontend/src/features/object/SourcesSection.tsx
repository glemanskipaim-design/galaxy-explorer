import type { Source } from '../../api/types'
import { ExternalLink } from '../../components/ui/ExternalLink'
import styles from './Object.module.css'

/** Credito exigido por cada fonte usada, com o texto de agradecimento LITERAL. */
export function SourcesSection({ sources }: { sources: Source[] }) {
  return (
    <section className={styles.sources} aria-labelledby="fontes-titulo">
      <h2 id="fontes-titulo" className={styles.sectionHeading}>
        Fontes e créditos
      </h2>
      <ul className={styles.sourceList}>
        {sources.map((s) => (
          <li key={s.code} className={styles.source}>
            <h3 className={styles.sourceName}>{s.name}</h3>
            <p className={styles.sourceLinks}>
              <ExternalLink href={s.homepageUrl}>site da fonte</ExternalLink>
              {s.termsUrl && <ExternalLink href={s.termsUrl}>termos de uso</ExternalLink>}
            </p>
            {s.acknowledgment && (
              <blockquote className={styles.acknowledgment}>{s.acknowledgment}</blockquote>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
