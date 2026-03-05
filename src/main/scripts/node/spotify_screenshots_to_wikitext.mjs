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
*/

process.stdout.setDefaultEncoding("utf8");
const args = process.argv.slice(2);

if (args.length === 0) {
  console.error("Usage: node spotify_screenshots_to_wikitext.mjs <images...> [outputfile]");
  process.exit(1);
}

// detect optional output file
let outputFile = null;

if (args[args.length - 1].endsWith(".wiki")) {
  outputFile = args.pop();
}

const patterns = args.map(p => p.replaceAll("\\", "/"));
const IMAGE_PATHS = fg.sync(patterns, { onlyFiles: true });

if (IMAGE_PATHS.length === 0) {
  console.error("No images matched:", args.join(" "));
  process.exit(1);
}

// ensure correct numeric ordering
IMAGE_PATHS.sort((a, b) =>
  a.localeCompare(b, undefined, { numeric: true })
);

if (IMAGE_PATHS.length === 0) {
  console.error("No images matched:", args.join(" "));
  process.exit(1);
}

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

  const groups = [];
  let i = 0;

  while (i < rows.length) {
    const start = i;
    const who = rows[i].added_by;
    i++;

    while (i < rows.length && rows[i].added_by === who) i++;

    groups.push({
      start,
      span: i - start,
      who
    });
  }

  const groupMap = new Map();
  groups.forEach(g => groupMap.set(g.start, g));

  const out = [];

  out.push('{| class="wikitable"}');
  out.push("! # !! Artist !! Song !! Added by !! Notes");

  rows.forEach((row, r) => {

    out.push("|-");

    const idx = escapeWiki(row.index);
    const artist = escapeWiki(row.artist);
    const song = escapeWiki(row.song);
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

  const images = IMAGE_PATHS.map(file => ({
    type: "input_image",
    image_url: toDataURL(file)
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
Extract playlist rows from these Spotify screenshots.

Rules:
- index = track number
- artist = artist exactly as displayed
- song = track title exactly as displayed
- added_by = exactly as displayed
- notes = ""
- do not invent rows
- return all visible rows
- sort by index
`
          },
          ...images
        ]
      }
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
                  artist: { type: "string" },
                  song: { type: "string" },
                  added_by: { type: "string" },
                  notes: { type: "string" }
                },
                required: ["index", "artist", "song", "added_by", "notes"]
              }
            }
          },
          required: ["rows"]
        }
      }
    }
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

main();
