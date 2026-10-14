const gateway = "http://localhost:8080";

export async function api(path, { method = "GET", token, body, idempotencyKey } = {}) {
  const headers = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (idempotencyKey) {
    headers["Idempotency-Key"] = idempotencyKey;
  }
  let response;
  try {
    response = await fetch(`${gateway}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  } catch {
    const error = new Error("连不上网关，请确认店铺已经启动");
    error.status = 0;
    throw error;
  }
  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      const error = new Error("店铺返回的内容无法读取");
      error.status = response.status;
      throw error;
    }
  }
  if (!response.ok) {
    const error = new Error(payload?.message || "请求没有完成");
    error.status = response.status;
    error.code = payload?.code;
    throw error;
  }
  return {
    status: response.status,
    payload,
    instancePort: response.headers.get("X-Instance-Port")
  };
}
