# Shajgoj Scraper (Spring Boot)

Scrapes products from https://shop.shajgoj.com/shop?page=N into a database and lets you
list, search, edit and delete them from a simple web page.

## Run
Requirements: Java 17+ and Maven.

    mvn spring-boot:run

Open http://localhost:8081 , set the page range (leave "To page" empty for all pages) and press **Run scraper**.
Products appear in the table as pages are saved. Re-running updates existing products (matched by URL slug) instead of duplicating them.

## What is saved
From the shop listing: name, link, image, regular/sale price, discount %, size, badge.
From each product's own page (tick "Also open each product page", on by default):
`sku`, `description`, `rating`, `numReviews`, `brand`, `category`, `subCategory`, `tags` (many per product) and the full
breadcrumb path (`categoryPath`). `category` is the first breadcrumb item (e.g. Skin) and `subCategory` the last one
(e.g. Skin Lightening). Rating and review count stay empty for products that have no reviews on the site.
Opening every product page is one extra request per product (1.5 s each by default), so a full run takes hours.
Products that already have details are skipped on the next run unless you tick "Re-read details".

## AI content with Gemini (free tier)
1. Create a free key at https://aistudio.google.com/apikey (no card needed).
2. Give it to the app as the environment variable `GEMINI_API_KEY`, then restart.
   * IntelliJ: Run > Edit Configurations > Environment variables > `GEMINI_API_KEY=your_key`
   * Windows terminal: `set GEMINI_API_KEY=your_key` then `mvn spring-boot:run`
   * Linux/macOS: `export GEMINI_API_KEY=your_key`
3. In the page, open the **AI content (Gemini)** panel, choose how many products (start with 5-10), press **Generate SEO content**.

For every product it writes: SEO name, slug, meta description, English description and 4 Bangla benefit lines, then draws
two pictures into `./data/images` (served at `/media/...`):
* `<slug>.jpg` - the real product photo on a clean white square (not changed by AI, so the label stays correct)
* `<slug>-benefits-bn.jpg` - the photo plus the Bangla benefit lines, drawn with the bundled Noto Sans Bengali font
  (so the Bangla spelling is always exactly what you see in the review box)

Everything starts as **Draft**. Press **SEO** on a row to read it, fix any line, **Save & redraw pictures**, and
**Save & approve**. Approved products are never overwritten by later batches.

Free-tier notes
* The batch waits 6.5 s between products (`gemini.delay-ms`) to stay under ~10 requests/minute. If Google's limit for
  your key is lower, raise it. When the daily free quota is used up the batch stops with a message; run it again the next
  day, it continues with products that have no content yet.
* `gemini.model=gemini-flash-latest` follows Google's current Flash model. If Google retires a name you get a clear
  error; open `/api/ai/models` to see the names your key can use and set `gemini.model`.
* Google may use free-tier prompts to improve its products. Product names and descriptions are all that is sent.
* Please have a Bangla speaker read the first batch, and check claims on cosmetics before publishing.
* Only the product photo's URL is downloaded for the pictures. PNG/JPEG/GIF work; WebP/AVIF photos are reported as failed.

## Database
* Default: file-based H2 in ./data (no setup). Console: http://localhost:8081/h2-console , JDBC URL `jdbc:h2:file:./data/shajgoj`, user `sa`, empty password.
* MySQL: edit `src/main/resources/application-mysql.properties`, then
  `mvn spring-boot:run -Dspring-boot.run.profiles=mysql`

## API
| Method | URL | Purpose |
|---|---|---|
| GET | /api/products?q=&page=&size=&sortBy=&dir= | list / search |
| PUT | /api/products/{id} | update |
| DELETE | /api/products/{id} | delete |
| POST | /api/scrape?startPage=1&endPage=&details=true&refresh=false | start scraping |
| GET | /api/scrape/status | progress |
| POST | /api/scrape/stop | stop |
| POST | /api/ai/generate?limit=10&images=true&regenerate=false | start AI batch |
| GET | /api/ai/status, /api/ai/models | progress, usable Gemini model names |
| POST | /api/ai/stop | stop the batch |
| POST | /api/ai/products/{id}/generate | write content for one product now |
| POST | /api/ai/products/{id}/images | redraw the two pictures from the saved text |
| PUT | /api/ai/products/{id}/seo | save edits / approve |

## Settings (application.properties)
`scraper.delay-ms` (pause between pages, default 1500), `scraper.max-pages`, `scraper.timeout-ms`.
Keep the delay polite and check the site's terms/robots.txt before scraping at scale.

## If nothing is scraped
The parser reads product links (`/product/<slug>`) and the price text in each card. If the site changes
its layout, adjust `ProductParser.java`. Run `mvn test` to check the parser against the sample HTML in `ProductParserTest`.
