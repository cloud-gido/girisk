import type { CSSProperties } from 'react';

type BrandMarkProps = {
  size?: number;
  style?: CSSProperties;
  className?: string;
};

/**
 * GiRisk 品牌标：青绿圆角底 + 雷达弧 + 风险波形。
 * 与 GIDO 主标（暖冷光环星象）明显区分，保留产品识别度。
 */
export default function BrandMark({ size = 36, style, className }: BrandMarkProps) {
  const uid = `gr-${size}`;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      style={style}
      aria-hidden
    >
      <defs>
        <linearGradient id={`${uid}-bg`} x1="6" y1="4" x2="34" y2="36" gradientUnits="userSpaceOnUse">
          <stop stopColor="#5EEAD4" />
          <stop offset="0.55" stopColor="#2DD4BF" />
          <stop offset="1" stopColor="#0D9488" />
        </linearGradient>
        <linearGradient id={`${uid}-line`} x1="10" y1="28" x2="30" y2="12" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFFFFF" stopOpacity="0.98" />
          <stop offset="1" stopColor="#ECFEFF" stopOpacity="0.88" />
        </linearGradient>
      </defs>
      <rect width="40" height="40" rx="12" fill={`url(#${uid}-bg)`} />
      <rect x="5" y="5" width="30" height="30" rx="9" fill="#FFFFFF" fillOpacity="0.16" />
      {/* 雷达弧 — 监测 */}
      <path
        d="M12 26.5c0-4.4 3.6-8 8-8s8 3.6 8 8"
        stroke="#FFFFFF"
        strokeOpacity="0.5"
        strokeWidth="1.55"
        strokeLinecap="round"
      />
      <path
        d="M15.2 26.5c0-2.65 2.15-4.8 4.8-4.8s4.8 2.15 4.8 4.8"
        stroke="#FFFFFF"
        strokeOpacity="0.88"
        strokeWidth="1.55"
        strokeLinecap="round"
      />
      {/* 风险波形 */}
      <path
        d="M10 24.5h3.2l2.1-5.2 3.4 10.2 2.6-7.4 2.1 3.6H30"
        stroke={`url(#${uid}-line)`}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="20" cy="26.5" r="1.55" fill="#FFFFFF" />
      {/* 细微高光点 — 设计层次 */}
      <circle cx="29.5" cy="12.5" r="1.1" fill="#FFFFFF" fillOpacity="0.55" />
    </svg>
  );
}
