# Проучване на структурата на imot.bg за нуждите на scraper проект

Дата на проучването: 2026-08-24
Метод: реален браузър (Claude in Chrome), DOM снапшот на живи страници.

Бележка за метода на извличане: HTML-ът по-долу е взет чрез `element.outerHTML` в браузъра, не чрез view-source. При извличането бяха премахнати повечето атрибути освен `class`, `id` и `href` (без query string в href), заради филтър на инструмента за копиране на данни от браузъра. Реалният HTML вероятно съдържа и допълнителни атрибути (`data-*`, `onclick`, `srcset`, tracking pixels в снимките и т.н.) — това е маркирано изрично където е приложимо.

---

## 1. robots.txt

```
Sitemap: https://www.imot.bg/sitemap/index.xml

User-agent: *
Disallow:
```

Сайтът е изцяло отворен за crawl-ване — няма забранени пътища.

---

## 2. URL на резултати с филтри

Филтри, приложени в примерното търсене: град София, тип "1-СТАЕН" (важно: на imot.bg типът "апартамент" и броят стаи са ЕДНО И СЪЩО поле — няма отделен избор "апартамент" + отделно "брой стаи"; директно избираш "1-СТАЕН", "2-СТАЕН" и т.н. от списъка "Вид имот"), макс. цена 150000 EUR, "В строеж" (= ново строителство / незавършени имоти).

```
https://www.imot.bg/obiavi/prodazhbi/grad-sofiya/ednostaen?price_max=150000&ybuild_type=2~3~
```

Структура на URL-а:
- `/obiavi/prodazhbi/` — тип сделка (продажби)
- `/grad-sofiya/` — локация (slug)
- `/ednostaen` — брой стаи/тип (slug: `ednostaen`=1-стаен, `dvustaen`=2-стаен и т.н.)
- `?price_max=150000` — макс. цена
- `&ybuild_type=2~3~` — филтър "година на строителство" (2 = В строеж/незавършени; кодирано с `~` разделител, вероятно позволява множество стойности)

Резултат от търсенето: 351 обяви общо, 40 на страница.

---

## 3. HTML на обяви от списъка с резултати

Контейнер за всяка обява: `div.item` с `id="ida<source_id>"`.

**Всички ключови полета (цена, район/град, площ, брой стаи/тип, кратко описание, линк, брокер) се виждат директно в HTML на списъка — не се зареждат динамично през JS.** Изображенията реално имат `src`/`srcset` с query параметри за CDN resize, които бяха премахнати при извличането по-долу (реалният HTML ги съдържа).

### Обява 1

```html
<div class="item TOP " id="ida1a178542044874881">
  <div class="photo">
    <div class="wrapper">
      <div class="big">
        <a href="//www.imot.bg/obiava-1a178542044874881-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="image saveSlink">
          <img class="promoLine">
          <img class="pic">
        </a>
      </div>
      <div class="small">
        <a href="//www.imot.bg/obiava-1a178542044874881-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="saveSlink"><img></a>
        <a href="//www.imot.bg/obiava-1a178542044874881-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="saveSlink"><img></a>
        <a href="//www.imot.bg/obiava-1a178542044874881-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="saveSlink"><img></a>
      </div>
    </div>
  </div>
  <div class="text">
    <div class="zaglavie">
      <a href="//www.imot.bg/obiava-1a178542044874881-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="title saveSlink">Продава 1-СТАЕН<location>град София, Витоша</location></a>
      <div class="price UP">
        <div>112 000 €</div>
      </div>
      <div class="like">
        <a id="star_1a178542044874881" class=""></a>
      </div>
    </div>
    <new></new>
    <div class="info">
      45 кв.м, Партер от 4, Ще бъде въведен в експлоатация 2027 г., !!ВИСОК КЛАС БУТИКОВА СГРАДА !!! БЪДЕЩА МЕТРОСТАНЦИЯ !!!СХЕМА НА ПЛАЩАНЕ 10% СЕГА !!! Представяме ви ..., тел.: 0875363330
    </div>
    <div class="seller">
      <div class="logo">
        <a href="//home2u3.imot.bg"><img></a>
      </div>
      <div class="sInfo">
        <div class="name"><a href="//home2u3.imot.bg">ХОУМ ТУ Ю БЪЛГАРИЯ</a></div>
        <div class="location">бул. Черни връх  1, ет. 2</div>
      </div>
    </div>
  </div>
  <div class="links">
    <div>
      <a href="//www.imot.bg/obiava-1a178542044874881-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="photos saveSlink">Повече детайли <strong>и 6 снимки</strong></a>
      <a id="notepad_1a178542044874881" class="like">Добави в бележника</a>
    </div>
    <div>
      <a id="mark_p1a178542044874881" class="markirai">Маркирай обявата</a>
    </div>
  </div>
</div>
```

### Обява 2 (идентична структура, различни стойности)

```html
<div class="item TOP " id="ida1a178515121683972">
  <div class="photo">...</div>
  <div class="text">
    <div class="zaglavie">
      <a href="//www.imot.bg/obiava-1a178515121683972-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="title saveSlink">Продава 1-СТАЕН<location>град София, Витоша</location></a>
      <div class="price ">
        <div>115 000 €</div>
      </div>
      <div class="like"><a id="star_1a178515121683972" class=""></a></div>
    </div>
    <new></new>
    <div class="info">
      44 кв.м, 7-ми ет. от 9, Ще бъде въведен в експлоатация 2026 г., ПРЕД АКТ 16!!! СРЕДЕН ЕТАЖ!!! ГЛЕДКА!!! СТАТУТ АПАРТАМЕНТ!!! Хоум ту ю представя едностаен апартамен ..., тел.: 0875363330
    </div>
    <div class="seller">
      <div class="logo"><a href="//home2umontevideo.imot.bg"><img></a></div>
      <div class="sInfo">
        <div class="name"><a href="//home2umontevideo.imot.bg">ХОУМ ТУ Ю БЪЛГАРИЯ АД - ОФИС МОНТЕВИДЕО</a></div>
        <div class="location">гр. София</div>
      </div>
    </div>
  </div>
  <div class="links">
    <div>
      <a href="//www.imot.bg/obiava-1a178515121683972-prodava-ednostaen-apartament-grad-sofiya-vitosha" class="photos saveSlink">Повече детайли <strong>и 10 снимки</strong></a>
      <a id="notepad_1a178515121683972" class="like">Добави в бележника</a>
    </div>
  </div>
</div>
```

### Обява 3 (потвърждение на структурата)

`id="ida1a178634371887119"`, цена 62 500 €, 45 кв.м, квартал Горна баня, агенция "НОВ ДОМ 1" (`novdom1sf.imot.bg`) — следва напълно същата структура като обяви 1 и 2 (`div.zaglavie` с title+location+price, `div.info` с площ/етаж/година/описание/телефон, `div.seller` с брокер).

### Важна забележка: промотирани "Жилищна сграда" карти

Първите 3 карти на страница 1 всъщност НЕ са единични обяви за апартаменти, а промотирани строителни проекти ("Жилищна сграда"). Техните линкове имат различен URL slug формат и по-кратко числено ID:

```
/obiava-8545-zhilishtna-sgrada-grad-sofiya-malinova-dolina
/obiava-8612-zhilishtna-sgrada-grad-sofiya-obelya-1
/obiava-7236-zhilishtna-sgrada-grad-sofiya-poduyane
```

Скрейпърът трябва да ги разпознава и пропуска (или обработва отделно), ако целта е само единични обяви за апартаменти. Разграничение: истинските обяви имат ID във формат `1a` + дълга числова последователност; проектите/сградите имат кратко чисто число.

---

## 4. URL на страница 2 (пагинация)

```
https://www.imot.bg/obiavi/prodazhbi/grad-sofiya/ednostaen/p-2?price_max=150000&ybuild_type=2~3~
```

Пагинацията е чрез **path сегмент** `/p-N` (не query параметър), добавен след типа стаи, преди query string-а. Всяка страница показва 40 обяви (потвърдено: "41 - 80 от общо 351 обяви" на стр. 2).

---

## 5. URL + HTML на конкретна обява (detail page)

```
https://www.imot.bg/obiava-1a178542044874881-prodava-ednostaen-apartament-grad-sofiya-vitosha
```

### Секция заглавие / цена / адрес / ID
Контейнер: `div.contactsBox` → `div.obTitle` + `div.Price`

```html
<div class="obTitle">
  <h1>
    Продава 1-СТАЕН
    <div>град София, Витоша</div>
    <span>Обява: 1a178542044874881</span>
  </h1>
</div>
<div class="Price">
  112 000 €
  <div class="m2">(2 489 €/m<sup>2</sup>)</div>
</div>
```

### Секция площ / етаж / газ / ТЕЦ / строителство
Контейнер: `div.adParams`

```html
<div class="adParams">
  <div>Площ:<br><strong>45 m<sup>2</sup></strong></div>
  <div>Етаж:<br><strong>Партер от 4</strong></div>
  <div>Газ:<br><strong>НЕ</strong></div>
  <div>ТEЦ:<br><strong>НЕ</strong></div>
  <div>Строителство:<br><strong>Тухла, </strong>Ще бъде въведен в експлоатация <strong>2027 г.</strong></div>
</div>
```

### Полета, видими САМО в обявата (не и в списъка с резултати)

- Точен адрес/квартал с линк "Виж карта" (координати/карта се зареждат допълнително)
- Дата на публикуване: "Публикувана в 17:07 на 30 юли 2026 год."
- Брой посещения: "Обявата е посетена 481 пъти"
- Дали цената е с включено ДДС
- Пълни данни за брокера/агенцията (адрес на офис, телефон, "В imot.bg от 2011 г.")
- Опция "Купи с кредит" с калкулатор на месечна вноска
- Пълна галерия със снимки (в списъка се виждат само 2-3 thumbnail-а)
- История на цената (`div.priceHistory`) — при промяна на цената

---

## 6. Идентификатор на обявата (source_id)

Стабилен уникален ID се среща на **три места**, винаги еднакъв:

1. **В URL slug-а** на обявата: `/obiava-1a178542044874881-prodava-ednostaen-apartament-...` → ID-то е частта веднага след `obiava-` и преди първото тире след него: `1a178542044874881`
2. **В `id` атрибута на картата в списъка**: `<div class="item" id="ida1a178542044874881">` → просто премахни префикса `ida`
3. **Explicitно в HTML на самата обява**: `<span>Обява: 1a178542044874881</span>`

### Препоръка за scraper-а

Извличай ID-то с regex по URL-а: `/obiava-([a-z0-9]+)-/` — най-надежден метод, работи еднакво и в списъка, и в detail страницата, без да зависи от точната DOM структура на картата.

**Формат:** буква + дълга числова последователност (напр. `1a178542044874881`) за единични обяви за апартаменти. За жилищните сгради/проекти ID-то е кратко число без буквен префикс (напр. `8545`) — различен формат, скрейпърът трябва да го разпознава отделно, ако иска да изключи тези карти.

---

## Обобщение за структурата на базата данни (предложение)

Въз основа на наблюдаваните полета, таблицата за обявите вероятно трябва да съдържа:

- `source_id` (text, unique) — извлечен от URL slug-а
- `source_url` (text)
- `title` (text) — напр. "Продава 1-СТАЕН"
- `city` (text)
- `district` (text) — квартал/район
- `price_eur` (numeric)
- `price_per_sqm_eur` (numeric, налично само в detail страницата)
- `area_sqm` (numeric)
- `floor` (text) — напр. "Партер от 4" (не е чисто число, съдържа текст)
- `construction_type` (text) — напр. "Тухла", "Панел", "ЕПК"
- `construction_status` (text) — "Завършен" / "В строеж"
- `construction_year` (integer, nullable) — годината на въвеждане в експлоатация
- `has_gas` (boolean)
- `has_district_heating` (boolean) — ТЕЦ
- `agency_name` (text, nullable)
- `agency_url` (text, nullable)
- `phone` (text, nullable)
- `description` (text) — кратко описание от списъка (пълното е само в detail страницата)
- `published_at` (timestamp, само от detail страницата)
- `photo_count` (integer)
- `scraped_at` (timestamp)
