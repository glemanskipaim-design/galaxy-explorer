import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { SearchBox } from '../../features/search/SearchBox'
import styles from './AppShell.module.css'
import { SkipLink } from './SkipLink'

function Logo() {
  return (
    <svg className={styles.logoMark} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="ge-logo" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#a5f3fc" />
          <stop offset="1" stopColor="#c4b5fd" />
        </linearGradient>
      </defs>
      <ellipse cx="16" cy="16" rx="14" ry="6" fill="none" stroke="url(#ge-logo)" strokeWidth="1.5" transform="rotate(-24 16 16)" />
      <circle cx="16" cy="16" r="5" fill="url(#ge-logo)" />
      <circle cx="28" cy="10.5" r="1.6" fill="#f0abfc" />
    </svg>
  )
}

/** Busca compacta do cabecalho (fora da home): envia para /?q=. */
function HeaderSearch() {
  const [value, setValue] = useState('')
  const navigate = useNavigate()
  return (
    <SearchBox
      variant="compact"
      value={value}
      onChange={setValue}
      onSubmit={() => {
        const q = value.trim()
        if (q) void navigate(`/?q=${encodeURIComponent(q)}`)
      }}
    />
  )
}

export function AppShell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const isHome = pathname === '/'
  return (
    <div className={styles.shell}>
      <SkipLink />
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link to="/" className={styles.brand}>
            <Logo />
            <span className={styles.wordmark}>Galaxy Explorer</span>
          </Link>
          {!isHome && (
            <div className={styles.headerSearch}>
              <HeaderSearch />
            </div>
          )}
        </div>
      </header>
      <main id="conteudo" className={styles.main} tabIndex={-1}>
        {children}
      </main>
      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <p>
            Cada valor exibido traz a fonte, a referência e a data de coleta. O que o site calcula
            (como a distância a partir da paralaxe) é marcado como tal.
          </p>
          <p className={styles.footerMeta}>
            Dados astronômicos: SIMBAD (CDS, Strasbourg) · créditos completos na página de cada objeto
          </p>
        </div>
      </footer>
    </div>
  )
}
