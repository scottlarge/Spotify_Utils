import fg from "fast-glob";
import fs from "node:fs";
import path from "node:path";
import OpenAI from "openai";

const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

// cheaper + still very good for OCR
const MODEL = "gpt-4.1-mini";

/*
CLI usage examples

node spotify_screenshots_to_wikitext.mjs shot1.png shot2.png
node spotify_screenshots_to_wikitext.mjs screenshots/*.png
node spotify_screenshots_to_wikitext.mjs screenshots/*.png wipe205.wiki

Windows examples:
node spotify_screenshots_to_wikitext.mjs "C:\Users\you\Pictures\Screenshots\*.png" wipe205.wiki
*/

process.stdout.setDefaultEncoding("utf8");
const args = process.argv.slice(2);

if (args.length === 0) {
  console.error("Usage: node spotify_screenshots_to_wikitext.mjs <images...> [outputfile]");
  process.exit(1);
}

// detect optional output file
let outputFile = null;
if (args[args.length - 1].toLowerCase().endsWith(".wiki")) {
  outputFile = args.pop();
}

// fast-glob expects POSIX-style patterns
const patterns = args.map((p) => p.replaceAll("\\", "/"));

const IMAGE_PATHS = fg.sync(patterns, { onlyFiles: true });

if (IMAGE_PATHS.length === 0) {
  console.error("No images matched:", args.join(" "));
  process.exit(1);
}

// ensure correct numeric ordering
IMAGE_PATHS.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));

function toDataURL(file) {
  const ext = path.extname(file).replace(".", "").toLowerCase() || "png";
  const b64 = fs.readFileSync(file, { encoding: "base64" });
  return `data:image/${ext};base64,${b64}`;
}

function clean(text) {
  if (text === null || text === undefined) return "";

  return String(text)
    .replace(/’/g, "'")
    .replace(/–/g, "-")
    .replace(/—/g, "-")
    .trim();
}

function escapeWiki(text) {
  return clean(text).replaceAll("|", "{{!}}");
}

function renderWikitext(rows) {
  rows = [...rows].sort((a, b) => a.index - b.index);

  // compute contiguous rowspan groups for "Added by"
  const groups = [];
  let i = 0;
  while (i < rows.length) {
    const start = i;
    const who = rows[i].added_by;
    i++;
    while (i < rows.length && rows[i].added_by === who) i++;
    groups.push({ start, span: i - start, who });
  }

  const groupMap = new Map();
  groups.forEach((g) => groupMap.set(g.start, g));

  const out = [];
  out.push('{| class="wikitable"}');
  out.push("! # !! Artist !! Song !! Added by !! Notes");

  rows.forEach((row, r) => {
    out.push("|-");

    const idx = escapeWiki(row.index);

    // IMPORTANT: use track_artist + track_title (not album)
    const artist = escapeWiki(row.track_artist);
    const song = escapeWiki(row.track_title);

    // Notes is intentionally blank; if you want album in notes:
    // const notes = escapeWiki(row.notes || row.album_title || "");
    const notes = escapeWiki(row.notes || "");

    const g = groupMap.get(r);

    if (g) {
      const who = escapeWiki(g.who);
      out.push(`| ${idx} || ${artist} || ${song} || rowspan="${g.span}" | ${who} || ${notes}`);
    } else {
      out.push(`| ${idx} || ${artist} || ${song} || ${notes}`);
    }
  });

  out.push("|}");
  return out.join("\n");
}

async function main() {
  const images = IMAGE_PATHS.map((file) => ({
    type: "input_image",
    image_url: toDataURL(file),
    detail: "high",
  }));

  const response = await client.responses.create({
    model: MODEL,

    input: [
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: `
Extract playlist rows from these Spotify screenshots (Spotify playlist LIST VIEW).

CRITICAL COLUMN MAPPING:
- track_title: the SONG TITLE in the first/main column (top line of the cell).
- track_artist: the ARTIST shown directly UNDER the title in the first/main column (smaller text).
- album_title: the ALBUM column value (separate column to the right).
- added_by: the "Added by" column value.
- notes: always "".

DO NOT swap track_title and track_artist.
DO NOT put album_title into track_title or track_artist.

Other rules:
- index = the row number on the far left
- keep punctuation/diacritics exactly as displayed
- do not invent rows; only include what is visible
- rows must be unique and sorted by index ascending

Return JSON that matches the schema exactly.
`,
          },
          ...images,
        ],
      },
    ],

    text: {
      format: {
        type: "json_schema",
        name: "playlist_rows",
        schema: {
          type: "object",
          additionalProperties: false,
          properties: {
            rows: {
              type: "array",
              items: {
                type: "object",
                additionalProperties: false,
                properties: {
                  index: { type: "integer" },

                  track_title: { type: "string" },
                  track_artist: { type: "string" },
                  album_title: { type: "string" },

                  added_by: { type: "string" },
                  notes: { type: "string" },
                },
                required: ["index", "track_title", "track_artist", "album_title", "added_by", "notes"],
              },
            },
          },
          required: ["rows"],
        },
      },
    },
  });

  const payload = JSON.parse(response.output_text);

  const wiki = renderWikitext(payload.rows);

  if (outputFile) {
    fs.writeFileSync(outputFile, wiki, { encoding: "utf8" });
    console.log(`Saved: ${outputFile}`);
  } else {
    console.log(wiki);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
