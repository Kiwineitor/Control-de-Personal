/**
 * useAvisoPrivacidad — aviso de privacidad y condiciones de uso del portal.
 *
 * Contenido y momento salen de la sección 10 del spec (`docs/monitoreo-gps-spec.md`):
 * el aviso se muestra en el primer ingreso y queda accesible después desde el menú
 * de perfil.
 *
 * Se muestra ANTES de pedir los permisos de ubicación y notificaciones, no después.
 * Además de ser lo correcto —el usuario acepta sabiendo qué se registra—, explicar
 * el porqué antes del diálogo del sistema mejora la tasa de concesión: un permiso
 * de ubicación "todo el tiempo" pedido a secas se deniega mucho más.
 *
 * La aceptación se guarda POR USUARIO: estos teléfonos se comparten entre turnos, y
 * quien entra después tiene que ver y aceptar el aviso por su cuenta.
 */
import { readonly, ref } from 'vue';

const PREFIJO_CLAVE = 'sisu:aviso-privacidad:';

/** ¿El aviso está en pantalla? */
const visible = ref(false);
/** ¿Es el consentimiento inicial (no se puede cerrar) o una relectura (sí)? */
const bloqueante = ref(false);

/** Resuelve la espera de asegurarConsentimiento() cuando el usuario acepta. */
let resolverEspera: (() => void) | null = null;

function clave(username: string): string {
  return `${PREFIJO_CLAVE}${username}`;
}

function yaAcepto(username: string): boolean {
  try {
    return localStorage.getItem(clave(username)) !== null;
  } catch {
    // Sin localStorage no se puede recordar la aceptación. Se prefiere volver a
    // mostrarlo que darlo por aceptado sin constancia.
    return false;
  }
}

function registrarAceptacion(username: string): void {
  try {
    localStorage.setItem(clave(username), new Date().toISOString());
  } catch {
    /* no bloquear el ingreso si el almacenamiento falla */
  }
}

/**
 * Si el usuario nunca aceptó el aviso, lo muestra y espera a que acepte.
 * Si ya lo aceptó, resuelve de inmediato y no interrumpe nada.
 *
 * Llamar ANTES de solicitar permisos o arrancar el seguimiento.
 */
export function asegurarConsentimiento(username: null | string): Promise<void> {
  if (!username || yaAcepto(username)) return Promise.resolve();

  bloqueante.value = true;
  visible.value = true;

  return new Promise<void>((resolve) => {
    resolverEspera = () => {
      registrarAceptacion(username);
      resolve();
    };
  });
}

/** Reabre el aviso como lectura informativa: se puede cerrar sin aceptar de nuevo. */
export function mostrarAvisoPrivacidad(): void {
  bloqueante.value = false;
  visible.value = true;
}

export function useAvisoPrivacidad() {
  /** Botón "Entiendo y acepto" del aviso inicial. */
  function aceptar() {
    visible.value = false;
    bloqueante.value = false;
    resolverEspera?.();
    resolverEspera = null;
  }

  /** Cierre de la relectura. En el aviso inicial no está disponible. */
  function cerrar() {
    if (bloqueante.value) return;
    visible.value = false;
  }

  return {
    visible: readonly(visible),
    bloqueante: readonly(bloqueante),
    aceptar,
    cerrar,
  };
}
