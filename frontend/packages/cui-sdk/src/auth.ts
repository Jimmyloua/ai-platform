import CryptoJS from 'crypto-js';

/**
 * Generate HMAC-SHA256 signature for AK/SK authentication
 */
export function generateSignature(
  secretKey: string,
  stringToSign: string
): string {
  return CryptoJS.HmacSHA256(stringToSign, secretKey).toString(CryptoJS.enc.Hex);
}

/**
 * Build the string to sign for request authentication
 */
export function buildStringToSign(
  method: string,
  path: string,
  timestamp: string,
  body?: string
): string {
  const parts = [method.toUpperCase(), path, timestamp];

  if (body) {
    const bodyHash = CryptoJS.SHA256(body).toString(CryptoJS.enc.Hex);
    parts.push(bodyHash);
  }

  return parts.join('\n');
}

/**
 * Sign a request with AK/SK authentication
 */
export function signRequest(
  accessKey: string,
  secretKey: string,
  method: string,
  path: string,
  timestamp: string,
  body?: string
): string {
  const stringToSign = buildStringToSign(method, path, timestamp, body);
  return generateSignature(secretKey, stringToSign);
}

/**
 * Verify a signature (for testing purposes)
 */
export function verifySignature(
  secretKey: string,
  stringToSign: string,
  signature: string
): boolean {
  const expected = generateSignature(secretKey, stringToSign);
  return expected === signature;
}

/**
 * Parse JWT token (without verification)
 */
export function parseJwt(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const payload = parts[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decoded);
  } catch {
    return null;
  }
}

/**
 * Check if JWT token is expired
 */
export function isJwtExpired(token: string): boolean {
  const payload = parseJwt(token);
  if (!payload || !payload.exp) return true;

  const expirationTime = (payload.exp as number) * 1000;
  return Date.now() >= expirationTime;
}

/**
 * Authentication configuration
 */
export interface AuthConfig {
  type: 'aksk' | 'jwt' | 'none';
  accessKey?: string;
  secretKey?: string;
  token?: string;
}

/**
 * Create auth headers for a request
 */
export function createAuthHeaders(
  config: AuthConfig,
  method: string,
  path: string,
  body?: string
): Record<string, string> {
  const timestamp = Date.now().toString();
  const headers: Record<string, string> = {
    'X-Timestamp': timestamp,
  };

  switch (config.type) {
    case 'aksk':
      if (!config.accessKey || !config.secretKey) {
        throw new Error('AK/SK authentication requires accessKey and secretKey');
      }
      headers['X-Access-Key'] = config.accessKey;
      headers['X-Signature'] = signRequest(
        config.accessKey,
        config.secretKey,
        method,
        path,
        timestamp,
        body
      );
      break;

    case 'jwt':
      if (!config.token) {
        throw new Error('JWT authentication requires token');
      }
      if (isJwtExpired(config.token)) {
        throw new Error('JWT token is expired');
      }
      headers['Authorization'] = `Bearer ${config.token}`;
      break;

    case 'none':
      // No additional auth headers
      break;
  }

  return headers;
}