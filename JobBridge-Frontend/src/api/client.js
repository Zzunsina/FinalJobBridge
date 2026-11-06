import axios from 'axios';

const client = axios.create({ baseURL: '/api', withCredentials: true });
let csrf;
let refreshing;

export function isReplacedSession(error) {
  return error.response?.status === 401 && (
    error.response.data?.fieldErrors?.some((entry) => entry.field === 'sessionReplaced') ||
    error.response.data?.message?.startsWith('다른 곳에서 로그인되어')
  );
}
function announceSessionEnd(error) {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('jobbridge:session-ended', {
    detail: { message: error.response?.data?.message || '로그인이 만료되었습니다.', replaced: isReplacedSession(error) },
  }));
}

export async function loadCsrf() {
  const { data } = await client.get('/auth/csrf');
  csrf = data.token;
}

client.interceptors.request.use((config) => {
  if (!['get', 'head', 'options'].includes(config.method)) {
    config.headers['X-XSRF-TOKEN'] = csrf;
  }
  return config;
});

export function refresh() {
  if (!refreshing) {
    refreshing = client.post('/auth/refresh').catch((error) => {
      if (error.response?.status === 401 && !isReplacedSession(error)) announceSessionEnd(error);
      throw error;
    }).finally(() => {
      refreshing = undefined;
    });
  }
  return refreshing;
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (isReplacedSession(error)) {
      announceSessionEnd(error);
      throw error;
    }
    const config = error.config;
    if (error.response?.status === 401 && !config.url.startsWith('/auth/') && !config.retried) {
      config.retried = true;
      await refresh();
      return client(config);
    }
    throw error;
  },
);

export default client;
export const message = (error) =>
  error.response?.data?.message || '서버에 연결할 수 없습니다. 백엔드 실행 상태를 확인해 주세요.';
