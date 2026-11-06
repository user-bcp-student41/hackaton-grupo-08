import { Component, input } from '@angular/core';

export type IconName =
  | 'user'
  | 'lock'
  | 'mail'
  | 'eye'
  | 'eye-off'
  | 'alert'
  | 'check'
  | 'arrow-left'
  | 'chevron-down'
  | 'chevron-right'
  | 'send'
  | 'list'
  | 'users'
  | 'home'
  | 'grid'
  | 'bell'
  | 'receipt'
  | 'clock'
  | 'x'
  | 'help'
  | 'file'
  | 'log-out'
  | 'shield';

/** Iconos de trazo (decorativos): el significado lo da siempre el texto o el aria-label del control que los contiene. */
@Component({
  selector: 'app-icon',
  template: `
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @switch (name()) {
        @case ('user') {
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" />
        }
        @case ('lock') {
          <rect x="5" y="11" width="14" height="10" rx="2" />
          <path d="M8 11V8a4 4 0 0 1 8 0v3" />
        }
        @case ('mail') {
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <path d="m3 7 9 6 9-6" />
        }
        @case ('eye') {
          <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
          <circle cx="12" cy="12" r="3" />
        }
        @case ('eye-off') {
          <path d="M3 3l18 18" />
          <path d="M10.6 6.1A10 10 0 0 1 12 6c6.4 0 10 6 10 6a17 17 0 0 1-3.2 3.9M6.7 7.7A17 17 0 0 0 2 12s3.6 7 10 7c1.6 0 3-.4 4.3-1" />
          <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
        }
        @case ('alert') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v6M12 16.5v.5" />
        }
        @case ('check') {
          <path d="m5 12.5 4.5 4.5L19 7.5" />
        }
        @case ('arrow-left') {
          <path d="M19 12H5M11 6l-6 6 6 6" />
        }
        @case ('chevron-down') {
          <path d="m6 9 6 6 6-6" />
        }
        @case ('chevron-right') {
          <path d="m9 6 6 6-6 6" />
        }
        @case ('send') {
          <path d="m22 2-11 11" />
          <path d="M22 2 15 22l-4-9-9-4 20-7Z" />
        }
        @case ('list') {
          <path d="M8 6h13M8 12h13M8 18h13" />
          <path d="M3 6h.01M3 12h.01M3 18h.01" />
        }
        @case ('users') {
          <circle cx="9" cy="8" r="3.5" />
          <path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" />
          <circle cx="17.5" cy="9" r="2.5" />
          <path d="M17 14.2c2.7.2 4.5 2.1 4.5 5.3" />
        }
        @case ('home') {
          <path d="m3 11 9-8 9 8" />
          <path d="M5 10v10h14V10" />
          <path d="M10 20v-6h4v6" />
        }
        @case ('grid') {
          <circle cx="6" cy="6" r="1.4" />
          <circle cx="18" cy="6" r="1.4" />
          <circle cx="6" cy="18" r="1.4" />
          <circle cx="18" cy="18" r="1.4" />
        }
        @case ('bell') {
          <path d="M6 9a6 6 0 1 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9Z" />
          <path d="M10 20a2 2 0 0 0 4 0" />
        }
        @case ('receipt') {
          <path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z" />
          <path d="M9 8h6M9 12h6" />
        }
        @case ('clock') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        }
        @case ('x') {
          <path d="M6 6l12 12M18 6 6 18" />
        }
        @case ('help') {
          <circle cx="12" cy="12" r="9" />
          <path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7" />
          <path d="M12 17v.5" />
        }
        @case ('file') {
          <path d="M7 3h7l4 4v14H7V3Z" />
          <path d="M14 3v4h4M10 12h5M10 16h5" />
        }
        @case ('log-out') {
          <path d="M10 4H5v16h5" />
          <path d="M16 8l4 4-4 4M20 12H9" />
        }
        @case ('shield') {
          <path d="M12 3 4.5 6v6c0 4.5 3 7.5 7.5 9 4.5-1.5 7.5-4.5 7.5-9V6L12 3Z" />
          <path d="m9 12 2.2 2.2L15.5 10" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      width: 1.25rem;
      height: 1.25rem;
    }
    svg {
      width: 100%;
      height: 100%;
    }
  `,
})
export class IconComponent {
  readonly name = input.required<IconName>();
}
