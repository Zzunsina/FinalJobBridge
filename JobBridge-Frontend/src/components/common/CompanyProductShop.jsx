import React, { useState } from 'react';
import CompanyPaymentModal from './CompanyPaymentModal';

export default function CompanyProductShop({ companyUser }) {
    // 판매할 상품 목록 정의
    const products = [
        {
            type: 'RESUME_READ_50',
            name: '인재 이력서 열람권 50건',
            price: 50000,
            description: '원하는 인재의 상세 이력서와 연락처를 50건까지 열람할 수 있습니다.'
        },
        {
            type: 'JOB_POSTING_TOP',
            name: '채용공고 프리미엄 상단 노출 (30일)',
            price: 99000,
            description: '30일 동안 채용공고가 메인 화면 상단에 노출되어 지원율을 높입니다.'
        }
    ];

    const [selectedProduct, setSelectedProduct] = useState(products[0]);

    return (
        <div className="p-6 max-w-4xl mx-auto">
            <h2 className="text-2xl font-bold mb-6">기업회원 서비스 이용권 구매</h2>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
                {products.map((p) => (
                    <div 
                        key={p.type}
                        className={`border rounded-lg p-6 cursor-pointer transition ${
                            selectedProduct.type === p.type ? 'border-blue-500 bg-blue-50' : 'border-gray-200'
                        }`}
                        onClick={() => setSelectedProduct(p)}
                    >
                        <h3 className="text-lg font-semibold mb-2">{p.name}</h3>
                        <p className="text-gray-600 text-sm mb-4">{p.description}</p>
                        <p className="text-xl font-bold text-blue-600">{p.price.toLocaleString()}원</p>
                    </div>
                ))}
            </div>

            {/* 선택된 상품을 결제 모달에 전달 */}
            <div className="bg-white border rounded-lg p-6 shadow-sm">
                <h3 className="text-lg font-bold mb-4">선택된 상품 결제하기</h3>
                <CompanyPaymentModal product={selectedProduct} companyUser={companyUser} />
            </div>
        </div>
    );
}