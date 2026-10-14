# Plan: fotos en las listas (catálogo de productos)

> Estado: **fases 1–4 implementadas** (2026-10-14). Almacenamiento: Supabase Storage, bucket privado `garaje-fotos`.
> Al implementar cada fase, marcarla aquí y en PROGRESS.md.

## Decisiones tomadas (2026-10-14)

- **Catálogo de productos con foto** ("Leche" + foto), reutilizable en todas las listas y con autocompletar.
- **Catálogo compartido con quien comparto listas**: el catálogo que usa una lista es **el de su dueño**.
  Quienes editan esa lista pueden usar y mejorar ese catálogo (agregar productos y poner fotos).
  En sus propias listas, cada persona usa el suyo. No se crea un concepto nuevo de "hogar" o "grupo".
- **Fuentes de la foto**: cámara o galería (se sube a un almacenamiento de objetos), **URL** (se guarda el enlace)
  y **foto propia por elemento** (reemplaza la del producto solo en ese elemento).

## Modelo de datos

```
products                                   # catálogo; dueño = owner_id
  id, owner_id → users
  name, normalized_name                    # normalizado: minúsculas, sin tildes ni espacios extra
  image_key?                               # objeto en el almacenamiento (foto subida)
  image_url?                               # enlace externo (si la foto vino de una URL)
  updated_by?, created_at, updated_at
  UNIQUE(owner_id, normalized_name)

checklist_items  (+ columnas)
  product_id? → products  ON DELETE SET NULL
  image_key?                               # foto propia del elemento (opcional)
```

**Qué foto se muestra**: la del elemento → si no hay, la del producto (subida o URL) → si no hay, ninguna.

**Vinculación automática**: al agregar un elemento a una lista se normaliza el texto y se busca en el
catálogo **del dueño de la lista**. Si existe, se vincula; si no, se crea el producto (sin foto).
Así el catálogo crece solo y el autocompletar mejora con el uso.

## Permisos

| Acción | Quién |
|---|---|
| Ver productos y fotos del catálogo de X | X y los miembros (cualquier rol) de alguna lista de X |
| Agregar o editar productos y fotos del catálogo de X | X y los **editores** de alguna lista de X |
| Foto propia de un elemento | Dueño y editores de esa lista |
| Ver fotos | Por enlaces firmados que vencen en ~1 h, que se generan solo si se tiene acceso |

## API (borrador)

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/lists/{id}/products?q=le` | Autocompletar desde el catálogo del dueño de la lista |
| POST | `/uploads` `{ contentType }` | Devuelve `{ uploadUrl, key }`: enlace temporal para subir **directo** al almacenamiento |
| PUT | `/products/{id}/image` `{ key }` o `{ url }` | Pone la foto del producto (subida o URL) y borra la anterior |
| DELETE | `/products/{id}/image` | Quita la foto del producto |
| PUT | `/lists/{id}/items/{itemId}/image` `{ key }` | Foto propia del elemento |
| DELETE | `/lists/{id}/items/{itemId}/image` | Vuelve a usar la foto del producto |

- `ChecklistItem` incluirá `productId` e `image: { url, source: "item" \| "product" }` con el enlace firmado o externo.
- Al adjuntar una `key`, el backend verifica que el objeto exista, sea imagen y mida menos de 2 MB.

## Almacenamiento

- **Render Free no sirve para archivos** (sin disco permanente). Se usa un almacenamiento de objetos **compatible con S3**
  (`boto3`), así que se puede cambiar de proveedor sin tocar el código: `STORAGE_ENDPOINT`, `STORAGE_BUCKET`,
  `STORAGE_KEY_ID`, `STORAGE_SECRET` como variables en Render.
- Bucket **privado**. Las claves de los objetos son `owners/{ownerId}/products/{productId}/{uuid}.jpg` e `lists/{listId}/items/{itemId}/{uuid}.jpg`.
- Limpieza: al reemplazar o quitar una foto, o al borrar elemento, lista o producto, se borra el objeto. Más adelante:
  una limpieza periódica de subidas que nunca se adjuntaron.

## App

- **Elegir foto**: selector de fotos de Android (no pide permisos) o cámara (`TakePicture` + FileProvider).
- **Comprimir en el teléfono**: lado mayor 1280 px, JPEG calidad ~80 (~100–200 KB). Al recodificar
  **se eliminan los metadatos EXIF**, incluida la ubicación GPS de la foto.
- **Mostrar**: miniatura de 40 dp en cada elemento y visor a pantalla completa al tocarla. Las imágenes se
  cargan y cachean con **Coil**.
- **Autocompletar**: al escribir en "¿Qué hay que comprar?" aparecen productos del catálogo con su miniatura.
- **Menú del elemento**: "Foto del producto", "Foto solo para este elemento", "Pegar URL de imagen", "Quitar foto".
- URLs externas: solo `https://`. Aviso: el sitio de origen puede ver que se cargó la imagen, y el enlace puede romperse.

## Fases

1. [x] **Catálogo sin fotos**: tabla `products`, vinculación automática y autocompletar (backend + app).
2. [x] **Almacenamiento + foto del producto** desde galería o cámara, miniaturas y visor.
3. [x] **Foto desde URL**.
4. [x] **Foto propia por elemento**.
5. [ ] Pantalla "Mis productos" (renombrar, cambiar foto, borrar) y limpieza de fotos huérfanas.

La fase 1 ya aporta algo útil (autocompletar) y no depende del almacenamiento.

## Decisiones pendientes

- **Proveedor de almacenamiento**: Cloudflare R2 (más espacio gratis, sin costo de descarga, pide tarjeta)
  o Supabase Storage (1 GB gratis, sin tarjeta). Verificar los planes vigentes al crear la cuenta.
- ¿Límite de espacio por usuario? Propuesta: 200 MB por catálogo (~1 000–2 000 fotos).
