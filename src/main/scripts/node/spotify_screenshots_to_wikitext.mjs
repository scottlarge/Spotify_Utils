import fs from "node:fs";
import OpenAI from "openai";

const client = new OpenAI({ apiKey: "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" });
const MODEL = "gpt-4.1";

// ---- read image paths from CLI ----
const IMAGE_PATHS = process.argv.slice(2);

if (IMAGE_PATHS.length === 0) {
  console.error("Usage: node spotify_screenshots_to_wikitext.mjs <image1> <image2> ...");
  process.exit(1);
}
// -----------------------------------

function toDataURL(path) {
  const ext = path.toLowerCase().endsWith(".jpg") || path.toLowerCase().endsWith(".jpeg")
    ? "jpeg"
    : path.toLowerCase().endsWith(".webp")
      ? "webp"
      : "png";

  const b64 = fs.readFileSync(path, { encoding: "base64" });
  return `data:image/${ext};base64,${b64}`;
}

function escapeWiki(text) {
  return String(text ?? "").replaceAll("|", "{{!}}");
}

function renderWikitext(rows) {
  rows = [...rows].sort((a, b) => a.index - b.index);

  const groups = [];
  let i = 0;

  while (i < rows.length) {
    const start = i;
    const who = rows[i].added_by;
    i++;

    while (i < rows.length && rows[i].added_by === who) {
      i++;
    }

    groups.push({ start, end: i, who, span: i - start });
  }

  const groupStartByRow = new Map();
  for (const g of groups) groupStartByRow.set(g.start, g);

  const out = [];
  out.push('{| class="wikitable"');
  out.push("! # !! Artist !! Song !! Added by !! Notes");

  for (let r = 0; r < rows.length; r++) {
    const row = rows[r];

    const idx = escapeWiki(row.index);
    const artist = escapeWiki(row.artist);
    const song = escapeWiki(row.song);
    const notes = escapeWiki(row.notes ?? "");

    const g = groupStartByRow.get(r);

    out.push("|-");

    if (g) {
      const who = escapeWiki(g.who);
      out.push(`| ${idx} || ${artist} || ${song} || rowspan="${g.span}" | ${who} || ${notes}`);
    } else {
      out.push(`| ${idx} || ${artist} || ${song} || ${notes}`);
    }
  }

  out.push("|}");
  return out.join("\n");
}

async function main() {

  const schema = {
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
  };

  const images = IMAGE_PATHS.map((path) => ({
    type: "input_image",
    image_url: toDataURL(path)
  }));

const response = await client.responses.create({
  model: MODEL,
  input: [
    {
      role: "user",
      content: [
        {
          type: "input_text",
          text: "Extract the playlist rows from these screenshots."
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

fs.writeFileSync("table.wiki", wiki, { encoding: "utf8" });
console.log("Wrote table.wiki");
}

main();
