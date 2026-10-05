// Icons inlined from Lucide (ISC, see public/licenses/lucide.txt) and Simple Icons (CC0).

import type { JSX } from '@solidjs/web';

interface IconProps {
  class?: string;
}

const LUCIDE_SVG_PROPS = {
  xmlns: 'http://www.w3.org/2000/svg',
  width: 24,
  height: 24,
  viewBox: '0 0 24 24',
  fill: 'none',
  style: { fill: 'none' },
  stroke: 'currentColor',
  'stroke-width': 2,
  'stroke-linecap': 'round',
  'stroke-linejoin': 'round',
} satisfies JSX.SVGElementTags['svg'];

const SIMPLE_ICONS_SVG_PROPS = {
  xmlns: 'http://www.w3.org/2000/svg',
  width: 24,
  height: 24,
  viewBox: '0 0 24 24',
  fill: 'currentColor',
} satisfies JSX.SVGElementTags['svg'];

// Source: https://lucide.dev/icons/arrow-left
export function ArrowLeftIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m12 19-7-7 7-7" />
      <path d="M19 12H5" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/arrow-up
export function ArrowUpIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m5 12 7-7 7 7" />
      <path d="M12 19V5" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/check
export function CheckIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/chevron-left
export function ChevronLeftIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/chevron-right
export function ChevronRightIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/download
export function DownloadIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M12 15V3" />
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <path d="m7 10 5 5 5-5" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/eye
export function EyeIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/eye-off
export function EyeOffIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49" />
      <path d="M14.084 14.158a3 3 0 0 1-4.242-4.242" />
      <path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143" />
      <path d="m2 2 20 20" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/flag
export function FlagIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/gavel
export function GavelIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m14 13-8.381 8.38a1 1 0 0 1-3.001-3l8.384-8.381" />
      <path d="m16 16 6-6" />
      <path d="m21.5 10.5-8-8" />
      <path d="m8 8 6-6" />
      <path d="m8.5 7.5 8 8" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/gauge
export function GaugeIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m12 14 4-4" />
      <path d="M3.34 19a10 10 0 1 1 17.32 0" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/image
export function ImageIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <rect width="18" height="18" x="3" y="3" rx="2" ry="2" />
      <circle cx="9" cy="9" r="2" />
      <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/image-off
export function ImageOffIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <line x1="2" x2="22" y1="2" y2="22" />
      <path d="M10.41 10.41a2 2 0 1 1-2.83-2.83" />
      <line x1="13.5" x2="6" y1="13.5" y2="21" />
      <line x1="18" x2="21" y1="12" y2="15" />
      <path d="M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59" />
      <path d="M21 15V5a2 2 0 0 0-2-2H9" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/key-round
export function KeyIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z" />
      <circle cx="16.5" cy="7.5" r=".5" fill="currentColor" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/list
export function ListIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M3 5h.01" />
      <path d="M3 12h.01" />
      <path d="M3 19h.01" />
      <path d="M8 5h13" />
      <path d="M8 12h13" />
      <path d="M8 19h13" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/lock
export function LockIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/log-out
export function LogOutIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m16 17 5-5-5-5" />
      <path d="M21 12H9" />
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/plus
export function PlusIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M5 12h14" />
      <path d="M12 5v14" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/refresh-cw
export function RefreshIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
      <path d="M21 3v5h-5" />
      <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
      <path d="M8 16H3v5" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/scan-search
export function ScanSearchIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M3 7V5a2 2 0 0 1 2-2h2" />
      <path d="M17 3h2a2 2 0 0 1 2 2v2" />
      <path d="M21 17v2a2 2 0 0 1-2 2h-2" />
      <path d="M7 21H5a2 2 0 0 1-2-2v-2" />
      <circle cx="12" cy="12" r="3" />
      <path d="m16 16-1.9-1.9" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/search
export function SearchIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m21 21-4.34-4.34" />
      <circle cx="11" cy="11" r="8" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/search-x
export function SearchXIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m13.5 8.5-5 5" />
      <path d="m8.5 8.5 5 5" />
      <circle cx="11" cy="11" r="8" />
      <path d="m21 21-4.3-4.3" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/shield
export function ShieldIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/shield-minus
export function ShieldMinusIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
      <path d="M9 12h6" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/shield-plus
export function ShieldPlusIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
      <path d="M9 12h6" />
      <path d="M12 9v6" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/trash
export function TrashIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
      <path d="M3 6h18" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/triangle-alert
export function TriangleAlertIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" />
      <path d="M12 9v4" />
      <path d="M12 17h.01" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/users
export function UsersIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <path d="M16 3.128a4 4 0 0 1 0 7.744" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
      <circle cx="9" cy="7" r="4" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/x
export function XIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </svg>
  );
}

// Source: https://lucide.dev/icons/external-link
export function ExternalLinkIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...LUCIDE_SVG_PROPS} class={props.class}>
      <path d="M15 3h6v6" />
      <path d="M10 14 21 3" />
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
    </svg>
  );
}

// Source: https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/github.svg
export function GitHubIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...SIMPLE_ICONS_SVG_PROPS} class={props.class}>
      <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
    </svg>
  );
}

// Source: https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/discord.svg
export function DiscordIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" {...SIMPLE_ICONS_SVG_PROPS} class={props.class}>
      <path d="M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z" />
    </svg>
  );
}
