import { useId, type FormEvent } from 'react'
import { SEARCH_MAX_LENGTH } from '../../config'
import { cx } from '../../components/ui/cx'
import { VisuallyHidden } from '../../components/ui/VisuallyHidden'
import styles from './SearchBox.module.css'

interface SearchBoxProps {
  value: string
  onChange: (v: string) => void
  onSubmit?: () => void
  variant: 'hero' | 'compact'
  autoFocus?: boolean
}

function SearchIcon() {
  return (
    <svg className={styles.icon} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M16 16l4.5 4.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

export function SearchBox({ value, onChange, onSubmit, variant, autoFocus }: SearchBoxProps) {
  const id = useId()
  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit?.()
  }
  return (
    <form role="search" className={cx(styles.form, styles[variant])} onSubmit={submit}>
      <label htmlFor={id}>
        <VisuallyHidden>Pesquisar um objeto celeste</VisuallyHidden>
      </label>
      <SearchIcon />
      <input
        id={id}
        className={styles.input}
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        maxLength={SEARCH_MAX_LENGTH}
        placeholder={variant === 'hero' ? 'Pesquisar um objeto… (M31, Betelgeuse, Sgr A*)' : 'Pesquisar um objeto…'}
        autoComplete="off"
        spellCheck={false}
        // autoFocus so na busca principal da home (pedido explicito do plano)
        // eslint-disable-next-line jsx-a11y/no-autofocus
        autoFocus={autoFocus}
      />
      {variant === 'compact' && (
        <button type="submit" className={styles.submit}>
          Buscar
        </button>
      )}
    </form>
  )
}
