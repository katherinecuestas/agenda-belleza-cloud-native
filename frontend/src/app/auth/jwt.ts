/**
 * Lee el contenido (payload) de un JWT. NO verifica la firma:
 * eso lo hacen el API Gateway y Spring con las claves de /jwks.
 */
export function decodificarJwt(token: string | null | undefined): Record<string, any> | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(payload), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}