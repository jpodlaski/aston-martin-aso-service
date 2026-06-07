// Matches API datetimes without timezone suffix to avoid browser timezone shifts.
const ISO_LOCAL_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

export function formatCustomerDateTime(value) {
    if (!value) return null;

    const match = value.match(ISO_LOCAL_PATTERN);
    if (!match) {
        return value;
    }

    const [, year, month, day, hour, minute] = match;
    // month is 0-indexed in JS Date
    const weekday = new Date(Number(year), Number(month) - 1, Number(day))
        .toLocaleDateString("en-GB", { weekday: "long" });

    return `${year}-${month}-${day}, ${weekday}, ${hour}:${minute}`;
}

export function toDatetimeLocalValue(value) {
    if (!value) return "";

    const match = value.match(ISO_LOCAL_PATTERN);
    return match ? `${match[1]}-${match[2]}-${match[3]}T${match[4]}:${match[5]}` : value.slice(0, 16);
}

export function hasCustomerAvailability(booking) {
    return Boolean(booking?.estimatedDropOffTime || booking?.availabilityNotes);
}
