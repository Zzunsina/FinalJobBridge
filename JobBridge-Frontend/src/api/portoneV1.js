let sdk;
let initializedCode;
export function loadPortOneV1() {
  if (window.IMP) return Promise.resolve(window.IMP);
  if (!sdk) {
    sdk = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://cdn.iamport.kr/v1/iamport.js';
      script.onload = () => {
        if (window.IMP) resolve(window.IMP);
        else {
          sdk = null;
          script.remove();
          reject(new Error('포트원 V1 결제창 초기화에 실패했습니다.'));
        }
      };
      script.onerror = () => {
        script.remove();
        sdk = null;
        reject(new Error('결제창을 불러올 수 없습니다. 네트워크를 확인해 주세요.'));
      };
      document.head.append(script);
    });
  }
  return sdk;
}
export function legacyPaymentParameters(checkout, returnUrl) {
  return {
    channelKey: checkout.channelKey,
    pay_method: 'card',
    merchant_uid: checkout.paymentId,
    name: checkout.orderName,
    amount: checkout.totalAmount,
    currency: 'KRW',
    buyer_name: checkout.customer.fullName,
    buyer_email: checkout.customer.email,
    buyer_tel: checkout.customer.phoneNumber,
    m_redirect_url: returnUrl,
  };
}
export function legacyCallback(response, orderId) {
  if (!response || response.error_code || response.success === false)
    throw new Error(
      response?.error_msg || '결제가 취소되거나 실패했습니다. 주문 내역에서 다시 확인해 주세요.',
    );
  if (!response.imp_uid || response.merchant_uid !== orderId)
    throw new Error('결제 응답이 주문과 일치하지 않습니다. 내역에서 결제 확인을 눌러 주세요.');
  return response;
}
export async function requestPortOneV1(checkout, returnUrl) {
  const api = await loadPortOneV1();
  if (initializedCode !== checkout.impCode) {
    api.init(checkout.impCode);
    initializedCode = checkout.impCode;
  }
  return new Promise((resolve, reject) => {
    api.request_pay(legacyPaymentParameters(checkout, returnUrl), (response) => {
      try {
        resolve(legacyCallback(response, checkout.paymentId));
      } catch (e) {
        reject(e);
      }
    });
  });
}
