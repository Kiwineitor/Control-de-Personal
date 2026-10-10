import type { RouteRecordStringComponent } from '@vben/types';
/**
 * accessMode: 'frontend' → el menú viene de los archivos de rutas,
 * NO del backend. Retornamos array vacío para evitar llamadas
 * a Spring Boot que devolverían HTML del login de Thymeleaf.
 */
export async function getAllMenusApi(): Promise<RouteRecordStringComponent[]> {
  return [];
}