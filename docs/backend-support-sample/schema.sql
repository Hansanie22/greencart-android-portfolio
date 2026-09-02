-- =================================================================
-- GreenCart Supermarket & Subscription Engine - TiDB / MySQL Schema
-- Optimized for TiDB Cloud Serverless & MySQL 8.0+
-- Developed by Kalatuwawage Hansanie Prabodha
-- =================================================================

CREATE DATABASE IF NOT EXISTS greencart_db;
USE greencart_db;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uid VARCHAR(128) UNIQUE NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) UNIQUE NOT NULL,
    phone_number VARCHAR(20),
    avatar_url TEXT,
    green_points INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Categories Table
CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon_name VARCHAR(60),
    description TEXT
);

-- 3. Products Table
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    category_id INT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    original_price DECIMAL(10, 2),
    stock_quantity INT DEFAULT 0,
    unit_measure VARCHAR(50) DEFAULT '1 item',
    image_url TEXT,
    is_subscription_eligible BOOLEAN DEFAULT TRUE,
    is_deal_of_the_day BOOLEAN DEFAULT FALSE,
    rating DECIMAL(2, 1) DEFAULT 5.0,
    review_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- 4. Orders Table
CREATE TABLE IF NOT EXISTS orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(60) UNIQUE NOT NULL,
    user_id INT,
    subtotal DECIMAL(10, 2) NOT NULL,
    shipping_fee DECIMAL(10, 2) DEFAULT 0.0,
    discount_amount DECIMAL(10, 2) DEFAULT 0.0,
    points_redeemed INT DEFAULT 0,
    total_amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(50) DEFAULT 'PAID',
    order_status VARCHAR(50) DEFAULT 'PROCESSING',
    delivery_address TEXT NOT NULL,
    delivery_lat DECIMAL(10, 7),
    delivery_lng DECIMAL(10, 7),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 5. Order Items Table
CREATE TABLE IF NOT EXISTS order_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
);

-- 6. Recurring Subscriptions Table
CREATE TABLE IF NOT EXISTS subscriptions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_code VARCHAR(60) UNIQUE NOT NULL,
    user_id INT,
    product_id INT,
    frequency_type VARCHAR(50) NOT NULL, -- 'DAILY', 'WEEKLY', 'MONTHLY'
    preferred_delivery_slot VARCHAR(50) DEFAULT 'Morning (8AM - 11AM)',
    status VARCHAR(30) DEFAULT 'ACTIVE', -- 'ACTIVE', 'PAUSED', 'CANCELLED'
    discount_percentage DECIMAL(5, 2) DEFAULT 15.00,
    next_delivery_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
);

-- 7. Gamified Scratch Cards & Loyalty Rewards
CREATE TABLE IF NOT EXISTS reward_scratch_cards (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    title VARCHAR(120) NOT NULL,
    reward_type VARCHAR(50) NOT NULL, -- 'POINTS', 'DISCOUNT_VOUCHER'
    reward_value DECIMAL(10, 2) NOT NULL,
    is_scratched BOOLEAN DEFAULT FALSE,
    scratched_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    CONSTRAINT fk_rewards_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- =================================================================
-- INITIAL SEED DATA (Ready-to-use Sample Data)
-- =================================================================

-- Seed Demo User
INSERT INTO users (uid, full_name, email, phone_number, avatar_url, green_points)
VALUES ('GC_USER_001', 'Kalatuwawage Hansanie Prabodha', 'customer@greencart.lk', '+94710000000', 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200', 420)
ON DUPLICATE KEY UPDATE full_name=VALUES(full_name);

-- Seed Categories
INSERT INTO categories (id, name, icon_name, description) VALUES
(1, 'Fresh Vegetables', 'ic_vegetables', 'Organically grown, pesticide-free harvest direct from local farmers'),
(2, 'Organic Fruits', 'ic_fruits', 'Naturally ripened seasonal fresh fruits with zero chemicals'),
(3, 'Dairy & Eggs', 'ic_dairy', 'Grass-fed dairy, artisanal cheeses and free-range farm eggs'),
(4, 'Bakery & Grains', 'ic_bakery', 'Rustic sourdough, whole wheat bread and organic pantry grains'),
(5, 'Beverages & Juices', 'ic_beverages', 'Cold-pressed raw juices, herbal infusions and organic nut milks')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Seed Products
INSERT INTO products (id, category_id, title, description, price, original_price, stock_quantity, unit_measure, image_url, is_subscription_eligible, is_deal_of_the_day, rating, review_count) VALUES
(101, 2, 'Organic Hass Avocados', 'Creamy, nutrient-packed Hass avocados harvested at peak ripeness. Perfect for salads and toast.', 380.00, 450.00, 50, '500g Pack', 'https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=500', TRUE, TRUE, 4.9, 142),
(102, 1, 'Hydroponic English Spinach', 'Crisp, washed hydroponic spinach leaves rich in iron, harvested within 4 hours of dispatch.', 220.00, 260.00, 65, '250g Bundle', 'https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=500', TRUE, FALSE, 4.8, 89),
(103, 3, 'Artisanal Almond Milk', 'Cold-pressed unsweetened almond milk with zero added preservatives and pure vanilla extract.', 680.00, 750.00, 28, '1 Litre Bottle', 'https://images.unsplash.com/photo-1550583724-b2692b85b150?w=500', TRUE, TRUE, 4.9, 64),
(104, 4, 'Rustic Sourdough Loaf', 'Naturally fermented 24-hour slow-baked rustic sourdough loaf with a crunchy golden crust.', 490.00, 550.00, 20, '1 Loaf (500g)', 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=500', TRUE, FALSE, 4.7, 112),
(105, 1, 'Organic Cherry Tomatoes', 'Sweet and juicy vine-ripened organic cherry tomatoes bursting with antioxidants.', 310.00, 360.00, 40, '300g Punnet', 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=500', TRUE, TRUE, 4.8, 76),
(106, 5, 'Cold-Pressed Green Detox Juice', 'Refreshing blend of organic celery, green apple, cucumber, kale, and cold-pressed ginger.', 450.00, 520.00, 35, '350ml Bottle', 'https://images.unsplash.com/photo-1613478223719-2ab802602423?w=500', TRUE, FALSE, 4.9, 94)
ON DUPLICATE KEY UPDATE title=VALUES(title);

-- Seed Active Recurring Subscription
INSERT INTO subscriptions (subscription_code, user_id, product_id, frequency_type, preferred_delivery_slot, status, discount_percentage, next_delivery_date)
VALUES ('SUB-GC-2026-001', 1, 101, 'WEEKLY', 'Morning (8AM - 11AM)', 'ACTIVE', 15.00, DATE_ADD(CURDATE(), INTERVAL 5 DAY))
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- Seed Gamification Scratch Cards
INSERT INTO reward_scratch_cards (user_id, title, reward_type, reward_value, is_scratched, expires_at)
VALUES (1, 'Shake-to-Win Daily Bonus', 'POINTS', 150.00, FALSE, DATE_ADD(NOW(), INTERVAL 7 DAY)),
       (1, 'Weekend Organic Super Saver', 'DISCOUNT_VOUCHER', 10.00, TRUE, DATE_ADD(NOW(), INTERVAL 14 DAY));
