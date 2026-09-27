interface DemoEnvironmentBannerProps {
  compact?: boolean;
}

export function DemoEnvironmentBanner({ compact = false }: DemoEnvironmentBannerProps) {
  return (
    <aside className={compact ? "demo-banner demo-banner-compact" : "demo-banner"} role="status">
      <strong>DEMO ENVIRONMENT</strong>
      <span>Synthetic data only. Data may be reset.</span>
    </aside>
  );
}
