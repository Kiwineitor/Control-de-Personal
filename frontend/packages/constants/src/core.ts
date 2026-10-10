/**
 * @zh_CN 登录页面 url 地址
 */
export const LOGIN_PATH = '/auth/login';

export interface LanguageOption {
  label: string;
  value: 'es-ES' | 'pt-BR';
}

/**
 * Supported languages
 */
export const SUPPORT_LANGUAGES: LanguageOption[] = [
  {
    label: 'Español',
    value: 'es-ES',
  },
  {
    label: 'Português',
    value: 'pt-BR',
  },
];
