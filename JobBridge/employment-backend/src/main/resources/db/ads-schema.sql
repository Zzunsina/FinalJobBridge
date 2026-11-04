CREATE TABLE IF NOT EXISTS ad_products (
 product_code VARCHAR(20) PRIMARY KEY, name VARCHAR(80) NOT NULL,
 description VARCHAR(300) NOT NULL, price INT NOT NULL, duration_days INT NOT NULL,
 max_slots INT NOT NULL, is_active BOOLEAN NOT NULL DEFAULT TRUE,
 version INT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS ad_orders (
 order_id VARCHAR(50) PRIMARY KEY, company_id BIGINT NOT NULL, product_code VARCHAR(20) NOT NULL,
 product_name VARCHAR(80) NOT NULL, price INT NOT NULL, vat INT NOT NULL, total_amount INT NOT NULL,
 duration_days INT NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
 job_id BIGINT, link_type VARCHAR(20) NOT NULL, link_url VARCHAR(500) NOT NULL,
 image_data MEDIUMBLOB, image_type VARCHAR(30),
 payment_status VARCHAR(30) NOT NULL DEFAULT 'READY', review_status VARCHAR(30) NOT NULL DEFAULT 'AWAITING_PAYMENT',
 refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE', refund_reason VARCHAR(500), review_memo VARCHAR(1000),
 paid_at TIMESTAMP NULL, reviewed_at TIMESTAMP NULL, reviewed_by BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(company_id) REFERENCES companies(company_id), FOREIGN KEY(product_code) REFERENCES ad_products(product_code),
 INDEX ix_ad_orders_company(company_id,created_at), INDEX ix_ad_orders_display(product_code,payment_status,review_status,start_date,end_date)
);

CREATE TABLE IF NOT EXISTS ad_feature_upgrades (upgrade_key VARCHAR(80) PRIMARY KEY, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS ad_order_simulations (order_id VARCHAR(50) PRIMARY KEY, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(order_id) REFERENCES ad_orders(order_id));
