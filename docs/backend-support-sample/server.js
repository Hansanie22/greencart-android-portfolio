/**
 * GreenCart - Companion Mock Backend API Server
 * Provides REST endpoints for Categories, Products, Cart Sync,
 * Orders, Subscriptions, GreenPoints, and FCM Token Registration.
 *
 * Developed by Kalatuwawage Hansanie Prabodha — Full-Stack Systems Portfolio Project
 */

const express = require('express');
const cors = require('cors');
const morgan = require('morgan');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json());
app.use(morgan('dev'));

// Sample In-Memory Database
const categories = [
  { id: 1, name: 'Fresh Vegetables', icon: 'ic_vegetables', itemCount: 28 },
  { id: 2, name: 'Organic Fruits', icon: 'ic_fruits', itemCount: 34 },
  { id: 3, name: 'Dairy & Eggs', icon: 'ic_dairy', itemCount: 19 },
  { id: 4, name: 'Bakery & Grains', icon: 'ic_bakery', itemCount: 22 },
  { id: 5, name: 'Beverages & Juices', icon: 'ic_beverages', itemCount: 15 }
];

const products = [
  {
    id: 101,
    title: 'Organic Hass Avocados',
    category: 'Organic Fruits',
    price: 380.0,
    originalPrice: 450.0,
    rating: 4.9,
    reviewCount: 142,
    unit: '500g',
    stock: 45,
    imageUrl: 'https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=500&auto=format&fit=crop&q=80',
    description: 'Farm-fresh creamy Hass avocados, 100% pesticide-free organic harvest.',
    subscriptionAvailable: true
  },
  {
    id: 102,
    title: 'Hydroponic English Spinach',
    category: 'Fresh Vegetables',
    price: 220.0,
    originalPrice: 260.0,
    rating: 4.8,
    reviewCount: 89,
    unit: '250g',
    stock: 60,
    imageUrl: 'https://images.unsplash.com/photo-1576045057995-568f588f82fb?w=500&auto=format&fit=crop&q=80',
    description: 'Crisp, washed hydroponic spinach leaves rich in iron and nutrients.',
    subscriptionAvailable: true
  },
  {
    id: 103,
    title: 'Farm Fresh Almond Milk',
    category: 'Dairy & Eggs',
    price: 680.0,
    originalPrice: 750.0,
    rating: 4.9,
    reviewCount: 64,
    unit: '1 Litre',
    stock: 25,
    imageUrl: 'https://images.unsplash.com/photo-1550583724-b2692b85b150?w=500&auto=format&fit=crop&q=80',
    description: 'Unsweetened cold-pressed artisan almond milk with zero preservatives.',
    subscriptionAvailable: true
  },
  {
    id: 104,
    title: 'Artisan Sourdough Loaf',
    category: 'Bakery & Grains',
    price: 490.0,
    originalPrice: 550.0,
    rating: 4.7,
    reviewCount: 112,
    unit: '1 Loaf',
    stock: 18,
    imageUrl: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=500&auto=format&fit=crop&q=80',
    description: 'Naturally fermented slow-baked rustic sourdough loaf with a crispy crust.',
    subscriptionAvailable: true
  }
];

let orders = [];
let subscriptions = [];
let fcmTokens = [];

// API Health Check
app.get('/api/health', (req, res) => {
  res.json({
    status: 'online',
    service: 'GreenCart Companion Backend API',
    version: '1.0.0',
    timestamp: new Date().toISOString()
  });
});

// Categories Endpoints
app.get('/api/categories', (req, res) => {
  res.json({ success: true, data: categories });
});

// Products Endpoints
app.get('/api/products', (req, res) => {
  const { category, search } = req.query;
  let results = [...products];

  if (category) {
    results = results.filter(p => p.category.toLowerCase().includes(category.toLowerCase()));
  }
  if (search) {
    results = results.filter(p => p.title.toLowerCase().includes(search.toLowerCase()) || p.description.toLowerCase().includes(search.toLowerCase()));
  }

  res.json({ success: true, count: results.length, data: results });
});

app.get('/api/products/:id', (req, res) => {
  const product = products.find(p => p.id === parseInt(req.params.id));
  if (!product) {
    return res.status(404).json({ success: false, message: 'Product not found' });
  }
  res.json({ success: true, data: product });
});

// Orders Endpoint
app.post('/api/orders', (req, res) => {
  const order = {
    orderId: 'GC-' + Date.now(),
    createdAt: new Date().toISOString(),
    status: 'CONFIRMED',
    ...req.body
  };
  orders.push(order);
  res.status(201).json({ success: true, message: 'Order created successfully', data: order });
});

app.get('/api/orders', (req, res) => {
  res.json({ success: true, count: orders.length, data: orders });
});

// Recurring Subscriptions Endpoint
app.post('/api/subscriptions', (req, res) => {
  const subscription = {
    subscriptionId: 'SUB-' + Date.now(),
    createdAt: new Date().toISOString(),
    status: 'ACTIVE',
    ...req.body
  };
  subscriptions.push(subscription);
  res.status(201).json({ success: true, message: 'Subscription schedule initiated', data: subscription });
});

app.get('/api/subscriptions', (req, res) => {
  res.json({ success: true, count: subscriptions.length, data: subscriptions });
});

// FCM Token Registration for Push Notifications
app.post('/api/fcm/register', (req, res) => {
  const { token, userId, deviceModel } = req.body;
  if (!token) {
    return res.status(400).json({ success: false, message: 'FCM token required' });
  }
  fcmTokens.push({ token, userId, deviceModel, registeredAt: new Date().toISOString() });
  res.json({ success: true, message: 'FCM push token registered successfully' });
});

app.listen(PORT, () => {
  console.log(`🌿 GreenCart Backend API running on http://localhost:${PORT}`);
});
