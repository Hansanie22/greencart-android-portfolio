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

// Sample In-Memory Store
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
    categoryId: 2,
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
    categoryId: 1,
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
    categoryId: 3,
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
    categoryId: 4,
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
  },
  {
    id: 105,
    categoryId: 1,
    title: 'Organic Cherry Tomatoes',
    category: 'Fresh Vegetables',
    price: 310.0,
    originalPrice: 360.0,
    rating: 4.8,
    reviewCount: 76,
    unit: '300g Punnet',
    stock: 40,
    imageUrl: 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea?w=500&auto=format&fit=crop&q=80',
    description: 'Sweet and juicy vine-ripened organic cherry tomatoes bursting with antioxidants.',
    subscriptionAvailable: true
  },
  {
    id: 106,
    categoryId: 5,
    title: 'Cold-Pressed Green Detox Juice',
    category: 'Beverages & Juices',
    price: 450.0,
    originalPrice: 520.0,
    rating: 4.9,
    reviewCount: 94,
    unit: '350ml Bottle',
    stock: 35,
    imageUrl: 'https://images.unsplash.com/photo-1613478223719-2ab802602423?w=500&auto=format&fit=crop&q=80',
    description: 'Refreshing blend of organic celery, green apple, cucumber, and ginger.',
    subscriptionAvailable: true
  }
];

let orders = [];
let subscriptions = [];
let fcmTokens = [];

// Root Landing Page Dashboard
app.get('/', (req, res) => {
  res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      <title>GreenCart Companion REST API</title>
      <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 40px 20px; line-height: 1.6; }
        .container { max-width: 800px; margin: 0 auto; background: #1e293b; padding: 32px; border-radius: 16px; border: 1px solid #334155; box-shadow: 0 10px 30px rgba(0,0,0,0.4); }
        .badge { display: inline-block; background: #059669; color: #fff; padding: 4px 12px; border-radius: 999px; font-weight: 600; font-size: 0.85rem; margin-bottom: 16px; }
        h1 { color: #34d399; margin-bottom: 8px; }
        p { color: #94a3b8; font-size: 1.05rem; }
        .endpoints { margin-top: 24px; border-top: 1px solid #334155; padding-top: 20px; }
        .endpoint-row { display: flex; align-items: center; justify-content: space-between; background: #0f172a; padding: 12px 16px; border-radius: 8px; margin-bottom: 10px; }
        .method { font-weight: bold; padding: 3px 8px; border-radius: 4px; font-size: 0.8rem; margin-right: 10px; }
        .get { background: #0284c7; color: #fff; }
        .post { background: #16a34a; color: #fff; }
        a { color: #38bdf8; text-decoration: none; font-weight: 500; }
        a:hover { text-decoration: underline; }
        .footer { margin-top: 30px; text-align: center; font-size: 0.85rem; color: #64748b; }
      </style>
    </head>
    <body>
      <div class="container">
        <div class="badge">🚀 LIVE ON RENDER</div>
        <h1>🌿 GreenCart REST API Server</h1>
        <p>Companion backend service for the GreenCart Native Android Mobile Application.</p>
        
        <div class="endpoints">
          <h3>📡 Available REST Endpoints:</h3>
          
          <div class="endpoint-row">
            <div><span class="method get">GET</span> <a href="/api/health" target="_blank">/api/health</a></div>
            <span style="color: #64748b; font-size: 0.85rem;">System Health Check</span>
          </div>

          <div class="endpoint-row">
            <div><span class="method get">GET</span> <a href="/api/categories" target="_blank">/api/categories</a></div>
            <span style="color: #64748b; font-size: 0.85rem;">Supermarket Categories</span>
          </div>

          <div class="endpoint-row">
            <div><span class="method get">GET</span> <a href="/api/products" target="_blank">/api/products</a></div>
            <span style="color: #64748b; font-size: 0.85rem;">Product Catalog & Filters</span>
          </div>

          <div class="endpoint-row">
            <div><span class="method get">GET</span> <a href="/api/orders" target="_blank">/api/orders</a></div>
            <span style="color: #64748b; font-size: 0.85rem;">Order History</span>
          </div>

          <div class="endpoint-row">
            <div><span class="method get">GET</span> <a href="/api/subscriptions" target="_blank">/api/subscriptions</a></div>
            <span style="color: #64748b; font-size: 0.85rem;">Recurring Subscriptions</span>
          </div>
        </div>

        <div class="footer">
          Developed by <strong>Kalatuwawage Hansanie Prabodha</strong> — Full-Stack Portfolio Project<br/>
          <a href="https://github.com/Hansanie22/greencart-android-portfolio" target="_blank">GitHub Repository</a> • 
          <a href="https://hansanie22.github.io/greencart-android-portfolio/" target="_blank">Live Mobile App Showcase</a>
        </div>
      </div>
    </body>
    </html>
  `);
});

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
  res.json({ success: true, count: categories.length, data: categories });
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
  console.log(`🌿 GreenCart Backend API running on port ${PORT}`);
});
