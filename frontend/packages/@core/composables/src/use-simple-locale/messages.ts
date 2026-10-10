export type Locale = 'es-ES' | 'pt-BR';

export const messages: Record<Locale, Record<string, string>> = {
  'es-ES': {
    cancel: 'Cancelar',
    collapse: 'Colapsar',
    confirm: 'Confirmar',
    expand: 'Expandir',
    prompt: 'Aviso',
    reset: 'Restablecer',
    submit: 'Enviar',
  },
  'pt-BR': {
    cancel: 'Cancelar',
    collapse: 'Recolher',
    confirm: 'Confirmar',
    expand: 'Expandir',
    prompt: 'Aviso',
    reset: 'Redefinir',
    submit: 'Enviar',
  },
};

export const getMessages = (locale: Locale) => messages[locale] ?? messages['es-ES'];
