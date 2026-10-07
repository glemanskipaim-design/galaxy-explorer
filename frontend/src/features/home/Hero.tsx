import styles from './Home.module.css'

export function Hero() {
  return (
    <div className={styles.hero}>
      <p className={styles.eyebrow}>Catálogo astronômico com proveniência</p>
      <h1 className={styles.title}>GALAXY EXPLORER</h1>
      <p className={styles.subtitle}>Explore o universo através de dados reais.</p>
    </div>
  )
}
