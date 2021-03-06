<?php

class TestAssertRegExp
{
    public function test() {
        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertTrue(preg_match('', '...') > 0)</weak_warning>;
        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertFalse(preg_match('', '...') > 0)</weak_warning>;

        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertSame(0, preg_match('', '...'))</weak_warning>;
        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertSame(1, preg_match('', '...'))</weak_warning>;

        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertNotSame(0, preg_match('', '...'))</weak_warning>;
        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertNotSame(1, preg_match('', '...'))</weak_warning>;

        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertEquals(0, preg_match('', '...'))</weak_warning>;
        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertEquals(1, preg_match('', '...'))</weak_warning>;

        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertNotEquals(0, preg_match('', '...'))</weak_warning>;
        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertNotEquals(1, preg_match('', '...'))</weak_warning>;
    }

    public function testWithMessages() {
        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertTrue(preg_match('', '...') > 0, '...')</weak_warning>;
        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertFalse(preg_match('', '...') > 0, '...')</weak_warning>;

        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertSame(0, preg_match('', '...'), '...')</weak_warning>;
        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertSame(1, preg_match('', '...'), '...')</weak_warning>;

        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertNotSame(0, preg_match('', '...'), '...')</weak_warning>;
        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertNotSame(1, preg_match('', '...'), '...')</weak_warning>;

        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertEquals(0, preg_match('', '...'), '...')</weak_warning>;
        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertEquals(1, preg_match('', '...'), '...')</weak_warning>;

        <weak_warning descr="[EA] 'assertRegExp(...)' would fit more here.">$this->assertNotEquals(0, preg_match('', '...'), '...')</weak_warning>;
        <weak_warning descr="[EA] 'assertNotRegExp(...)' would fit more here.">$this->assertNotEquals(1, preg_match('', '...'), '...')</weak_warning>;
    }
}