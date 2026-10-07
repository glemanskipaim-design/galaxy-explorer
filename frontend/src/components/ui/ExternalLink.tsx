import type { ReactNode } from 'react'
import { safeUrl } from '../../domain/safeUrl'
import styles from './ExternalLink.module.css'
import { VisuallyHidden } from './VisuallyHidden'

/**
 * UNICO lugar que cria `<a>` para fora do site. `href` passa por safeUrl: so `https:`;
 * qualquer outra coisa vira texto puro (sem link).
 */
export function ExternalLink({ href, children }: { href: string; children: ReactNode }) {
  const url = safeUrl(href)
  if (url === null) return <span>{children}</span>
  return (
    <a className={styles.link} href={url} target="_blank" rel="noopener noreferrer">
      {children}
      <span aria-hidden="true" className={styles.arrow}>
        ↗
      </span>
      <VisuallyHidden> (abre em nova aba)</VisuallyHidden>
    </a>
  )
}
