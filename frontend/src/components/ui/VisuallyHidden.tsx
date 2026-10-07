import type { ReactNode } from 'react'
import styles from './VisuallyHidden.module.css'

/** Texto so para leitor de tela. */
export function VisuallyHidden({ children }: { children: ReactNode }) {
  return <span className={styles.hidden}>{children}</span>
}
