/**
 * A minimal, dependency-free ZIP writer.
 *
 * Bulk export hands the visitor dozens of `.conf` files at once. Firing dozens
 * of downloads is what the old converter did and browsers now block it, so the
 * files are packed into one archive instead.
 *
 * Only the "stored" method is implemented: configuration files are a few
 * hundred bytes each, deflate would need a compressor in the bundle, and an
 * uncompressed archive opens in every unpacker. Timestamps are fixed to the
 * start of the DOS epoch so the same input always produces the same bytes,
 * which is what makes the output testable.
 */

const LOCAL_HEADER_SIGNATURE = 0x04034b50
const CENTRAL_HEADER_SIGNATURE = 0x02014b50
const END_OF_CENTRAL_SIGNATURE = 0x06054b50

/** 1980-01-01 00:00, the earliest timestamp the DOS format can express. */
const DOS_TIME = 0
const DOS_DATE = 0x0021

/** UTF-8 names, no data descriptor, no encryption. */
const FLAG_UTF8 = 0x0800
const METHOD_STORE = 0
const VERSION = 20

const CRC_TABLE = buildCrcTable()

function buildCrcTable() {
	const table = new Uint32Array(256)
	for (let index = 0; index < 256; index += 1) {
		let value = index
		for (let bit = 0; bit < 8; bit += 1) {
			value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1
		}
		table[index] = value >>> 0
	}
	return table
}

/** CRC-32 as ZIP defines it. */
export function crc32(bytes) {
	let crc = 0xffffffff
	for (const byte of bytes) {
		crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8)
	}
	return (crc ^ 0xffffffff) >>> 0
}

function encode(text) {
	return new TextEncoder().encode(text)
}

/**
 * Packs `[{ name, text }]` into an archive.
 *
 * @returns the archive bytes; wrap them in a Blob to hand them to the browser.
 */
export function createZip(files) {
	const entries = files.map((file) => {
		const name = encode(file.name)
		const data = file.bytes ?? encode(file.text ?? "")
		return { name, data, crc: crc32(data) }
	})

	const localSize = entries.reduce((total, entry) => total + 30 + entry.name.length + entry.data.length, 0)
	const centralSize = entries.reduce((total, entry) => total + 46 + entry.name.length, 0)

	const output = new Uint8Array(localSize + centralSize + 22)
	const view = new DataView(output.buffer)
	let offset = 0

	for (const entry of entries) {
		entry.offset = offset

		view.setUint32(offset, LOCAL_HEADER_SIGNATURE, true)
		view.setUint16(offset + 4, VERSION, true)
		view.setUint16(offset + 6, FLAG_UTF8, true)
		view.setUint16(offset + 8, METHOD_STORE, true)
		view.setUint16(offset + 10, DOS_TIME, true)
		view.setUint16(offset + 12, DOS_DATE, true)
		view.setUint32(offset + 14, entry.crc, true)
		view.setUint32(offset + 18, entry.data.length, true)
		view.setUint32(offset + 22, entry.data.length, true)
		view.setUint16(offset + 26, entry.name.length, true)
		view.setUint16(offset + 28, 0, true)
		offset += 30

		output.set(entry.name, offset)
		offset += entry.name.length
		output.set(entry.data, offset)
		offset += entry.data.length
	}

	const centralStart = offset

	for (const entry of entries) {
		view.setUint32(offset, CENTRAL_HEADER_SIGNATURE, true)
		view.setUint16(offset + 4, VERSION, true)
		view.setUint16(offset + 6, VERSION, true)
		view.setUint16(offset + 8, FLAG_UTF8, true)
		view.setUint16(offset + 10, METHOD_STORE, true)
		view.setUint16(offset + 12, DOS_TIME, true)
		view.setUint16(offset + 14, DOS_DATE, true)
		view.setUint32(offset + 16, entry.crc, true)
		view.setUint32(offset + 20, entry.data.length, true)
		view.setUint32(offset + 24, entry.data.length, true)
		view.setUint16(offset + 28, entry.name.length, true)
		view.setUint16(offset + 30, 0, true)
		view.setUint16(offset + 32, 0, true)
		view.setUint16(offset + 34, 0, true)
		view.setUint16(offset + 36, 0, true)
		view.setUint32(offset + 38, 0, true)
		view.setUint32(offset + 42, entry.offset, true)
		offset += 46

		output.set(entry.name, offset)
		offset += entry.name.length
	}

	view.setUint32(offset, END_OF_CENTRAL_SIGNATURE, true)
	view.setUint16(offset + 4, 0, true)
	view.setUint16(offset + 6, 0, true)
	view.setUint16(offset + 8, entries.length, true)
	view.setUint16(offset + 10, entries.length, true)
	view.setUint32(offset + 12, offset - centralStart, true)
	view.setUint32(offset + 16, centralStart, true)
	view.setUint16(offset + 20, 0, true)

	return output
}
