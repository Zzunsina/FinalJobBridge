import axios from 'axios';

const client = axios.create({ baseURL: '/api', withCredentials: true });
let csrf;
let refreshing;

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
    refreshing = client.post('/auth/refresh').finally(() => {
      refreshing = undefined;
    });
  }
  return refreshing;
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
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
