-- Category tree: twelve top-level categories and their subcategories.
-- Copied from the original database/seed.sql so the reference data is versioned
-- with the schema rather than applied by hand.


INSERT INTO categories (name, description, icon, parent_category_id) VALUES
('Textbooks', 'Academic textbooks and course materials', 'book', NULL),
('Electronics', 'Electronic devices and accessories', 'smartphone', NULL),
('Furniture', 'Dorm and apartment furniture', 'home', NULL),
('Clothing & Accessories', 'Apparel, shoes, and accessories', 'shirt', NULL),
('Services', 'Student services including tutoring', 'briefcase', NULL),
('Transportation', 'Vehicles and rideshare opportunities', 'car', NULL),
('Sports & Recreation', 'Sports equipment and recreational items', 'activity', NULL),
('School Supplies', 'Stationery, notebooks, and school essentials', 'pencil', NULL),
('Beauty & Personal Care', 'Beauty products and personal care items', 'sparkles', NULL),
('Kitchen & Dining', 'Kitchen appliances and dining items', 'utensils', NULL),
('Tickets & Events', 'Event tickets and entertainment', 'ticket', NULL),
('Other', 'Miscellaneous items', 'package', NULL);

-- Subcategories for Textbooks
INSERT INTO categories (name, description, icon, parent_category_id) VALUES
('Engineering', 'Engineering textbooks', NULL, (SELECT id FROM categories WHERE name = 'Textbooks')),
('Business', 'Business and economics textbooks', NULL, (SELECT id FROM categories WHERE name = 'Textbooks')),
('Science', 'Science textbooks', NULL, (SELECT id FROM categories WHERE name = 'Textbooks')),
('Mathematics', 'Math textbooks and study guides', NULL, (SELECT id FROM categories WHERE name = 'Textbooks')),
('Liberal Arts', 'Liberal arts textbooks', NULL, (SELECT id FROM categories WHERE name = 'Textbooks'));

-- Subcategories for Electronics
INSERT INTO categories (name, description, icon, parent_category_id) VALUES
('Laptops & Computers', 'Laptops, desktops, and accessories', NULL, (SELECT id FROM categories WHERE name = 'Electronics')),
('Phones & Tablets', 'Mobile devices and tablets', NULL, (SELECT id FROM categories WHERE name = 'Electronics')),
('Audio & Headphones', 'Headphones, speakers, and audio equipment', NULL, (SELECT id FROM categories WHERE name = 'Electronics')),
('Gaming', 'Gaming consoles and accessories', NULL, (SELECT id FROM categories WHERE name = 'Electronics')),
('Cameras & Photo', 'Cameras and photography equipment', NULL, (SELECT id FROM categories WHERE name = 'Electronics'));

-- Subcategories for Furniture
INSERT INTO categories (name, description, icon, parent_category_id) VALUES
('Beds & Mattresses', 'Beds, mattresses, and bedding', NULL, (SELECT id FROM categories WHERE name = 'Furniture')),
('Desks & Chairs', 'Study desks and seating', NULL, (SELECT id FROM categories WHERE name = 'Furniture')),
('Storage & Organization', 'Shelves, drawers, and storage solutions', NULL, (SELECT id FROM categories WHERE name = 'Furniture')),
('Living Room', 'Sofas, coffee tables, and living room furniture', NULL, (SELECT id FROM categories WHERE name = 'Furniture'));

-- Subcategories for Services
INSERT INTO categories (name, description, icon, parent_category_id) VALUES
('Tutoring', 'Academic tutoring services', NULL, (SELECT id FROM categories WHERE name = 'Services')),
('Photography', 'Photography and videography services', NULL, (SELECT id FROM categories WHERE name = 'Services')),
('Design & Creative', 'Graphic design and creative services', NULL, (SELECT id FROM categories WHERE name = 'Services')),
('Tech Support', 'Computer repair and tech assistance', NULL, (SELECT id FROM categories WHERE name = 'Services'));

-- Subcategories for Transportation
INSERT INTO categories (name, description, icon, parent_category_id) VALUES
('Bikes & Scooters', 'Bicycles, e-scooters, and accessories', NULL, (SELECT id FROM categories WHERE name = 'Transportation')),
('Vehicles', 'Cars and motorcycles for sale', NULL, (SELECT id FROM categories WHERE name = 'Transportation'));
