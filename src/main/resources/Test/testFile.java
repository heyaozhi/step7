Class testFile {
    public static void main(String[] args) {
        let c = color1(0, 255, 0, 255);
        assertEqual(c.toHwbString1(), "hwb(120deg, 0%, 0%, 100%)");
        assertTrue(c.getAlpha1() >= 0 && c.getAlpha1() <= 255);
        let a = c.getAlpha1() / 255 * 100;
    }
}

