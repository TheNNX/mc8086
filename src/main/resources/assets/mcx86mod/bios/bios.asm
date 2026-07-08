; BIOS source for MCX86 BIOS by Marcin Jabłoński,
; based partially on http://www.megalith.co.uk/8086tiny by Adrian Cable
[org 0x0000]
[bits 16]
[cpu 8086]

BDA_EQUIPMENT equ 0x410
BDA_MEMSIZE equ 0x412
BDA_KBD_SHIFT_STATUS1 equ 0x417
BDA_KBD_SHIFT_STATUS2 equ 0x418
BDA_KBDHEAD equ 0x41A
BDA_KBDTAIL equ 0x41C
BDA_KBDBUF  equ 0x41E
BDA_DISK_STATUS equ 0x441
BDA_VIDEO_MODE equ 0x449
BDA_NUM_COLS equ 0x44A
BDA_CURPOS_PAGES equ 0x450
BDA_ACTIVE_PAGE equ 0x462
BDA_HDDNUM  equ 0x475
BDA_KBDBUF_START_OFFSET  equ 0x480
BDA_KBDBUF_END_OFFSET equ 0x482
BDA_NUM_ROWS equ 0X484

KBD_SCANCODE_LSHIFT equ 0x2A
KBD_SCANCODE_RSHIFT equ 0x36
KBD_SCANCODE_ALT    equ 0x38
KBD_SCANCODE_CTRL   equ 0x1D
KBD_SCANCODE_CPSLCK equ 0x3A
KBD_SCANCODE_EXT    equ 0xE0



boot:
    mov cx, cs
    mov ds, cx
    xor cx, cx
    mov es, cx
    mov ss, cx
    mov sp, 0xFFFF

    call initIvt

    mov ax, 0x40
    mov es, ax
    mov di, 0
    mov ax, 0
    mov cx, 0x100
    rep stosb

    call initVideoMem

    mov word [es:BDA_EQUIPMENT], 0b0000000000100001
    mov word [es:BDA_MEMSIZE], 0x280

    mov word [es:BDA_KBDHEAD], BDA_KBDBUF - 0x400
    mov word [es:BDA_KBDTAIL], BDA_KBDBUF - 0x400

    mov byte [es:BDA_VIDEO_MODE], 3

    mov word [es:BDA_KBDBUF_END_OFFSET], BDA_KBDBUF - 0x400
    mov word [es:BDA_KBDBUF_START_OFFSET], BDA_KBDBUF - 0x400 + 32

    mov di, 0x0000
    mov si, biosBannerStr
    call printStringWithInt

    call chechIDEPrimaryMaster

    ; Params:
    ;   AL - sector count
    ;   CH - cylinder
    ;   CL - sector
    ;   DH - head
    ;   DL - drive
    ;   ES:BX - buffer
    mov al, 1
    mov ah, 2
    mov ch, 0
    mov cl, 1
    xor dh, dh
    mov bx, 0x07C0
    mov es, bx
    xor bx, bx
    mov dl, 0x0
    int 13h

    xchg bx, bx
    cmp word [es:bx+510], 0xaa55
    jne .notBootable

    jmp 0x07C0:0x0000

.hlt:
    hlt
    jmp .hlt

.notBootable:
    mov si, noBootableDevice
    call printStringWithInt
    jmp .hlt

smallSleep:
    mov cx, 0xFF
.sleep:
    dec cx
    jcxz .endl
    jmp .sleep
.endl:
    ret

int3h:
    jmp boot.hlt

chechIDEPrimaryMaster:
    ; Select drive
    mov dx, 0x1F6
    mov al, 0xA0
    out dx, al
    call smallSleep
    mov dx, 0x1F7
    mov al, 0xEC
    out dx, al
    call smallSleep
    mov dx, 0x1F7
    in al, dx
    cmp al, 0
    jz short .noDevice
.yesDevice:
    inc byte [es:BDA_HDDNUM]
    push es
    push di
    mov di, 0x0000
    mov cx, 0x07C0
    mov es, cx
    mov cx, 256
.readBuffer:
    mov dx, 0x1F0
    in ax, dx
    stosw
    dec cx
    jcxz .endReadBuffer
    jmp .readBuffer
.endReadBuffer:
    pop di
    mov si, primaryIdeSizeStr
    call printStringWithInt

    mov ax, [es:120]
    mov cl, 1
    shr ax, cl
    mov bx, 10
    mov di, 256
    call printNum

    push ds
    push es
    pop ds
    mov si, 256
    call printStringWithInt
    pop ds

    mov si, strUnitKb
    call printStringWithInt

    pop es
    ret
.noDevice:
    mov si, noPrimaryIde
    call printStringWithInt
    ret


; Params:
;   AL - sector count
;   CH - cylinder
;   CL - sector
;   DH - head
;   DL - drive
;   ES:BX - buffer
readSectorsFromDrive:
    push es
    push cx
    push dx
    push di
    ;test dl, 0x80
    ;jnz short .harddrive
;.floppy:
    ;jmp $
.harddrive:

    push ax

    mov al, dh
    xor ah, ah
    out 0xE9, ax

    mov al, ch
    out 0xE9, ax

    mov al, cl
    and ax, 0b111111
    out 0xE9, ax

    pop ax

    push ax
    mov di, bx
    ; TODO: select drive/controller correctly
    ; So far only the primary master is supported.

    ; Sector count in AH
    mov ah, al
    xchg dh, al

    ; Select drive
    mov dx, 0x1F6
    or  al, 0xA0
    out dx, al

    ; Select sector count
    add dx, 2 - 6
    mov al, ah
    out dx, al

    ; Select sector number
    add dx, 3 - 2
    mov al, cl
    out dx, al

    ; Cylinder low
    add dx, 4 - 3
    mov al, ch
    out dx, al

    ; Cylinder high
    add dx, 5 - 4
    xor al, al
    out dx, al

    add dx, 7 - 5
    mov al, 0x20
    out dx, al

    times 4 in al, dx

.poll:
    in al, dx
    test al, 0x80
    jne short .poll
    test al, 0x08
    je short .poll
    test al, 0x21
    jne short .fail
.sectorReady:
    add dx, 0 - 7
    mov cx, 256

    push ax
.loop:
    in ax, dx
    stosw
    dec cx
    jnz .loop

    add dl, 7 - 0
    times 4 in al, dx
    pop ax

    dec ah
    jnz short .poll
.success:
    xor ax, ax
    mov es, ax
    pop ax
    ; Clear disk status - no error
    xor ah, ah

    mov [es:BDA_DISK_STATUS], ah
    pop di
    pop dx
    pop cx
    pop es
    jmp reachStackClc
.fail:
    mov dh, ah
    xor ax, ax
    mov es, ax
    pop ax
    ; TODO: return undefined error for now
    sub al, dh
    mov ah, 0xBB
    mov [es:BDA_DISK_STATUS], ah
    pop di
    pop dx
    pop cx
    pop es
    jmp reachStackStc

unimplemented:
    ret

unimplementedIret:
    jmp reachStackClc

printStringWithInt:
.loop:
    lodsb
    test al, al
    jz .endloop
    mov ah, 0x0E
    out 0xE8, al
    int 10h
    jmp printStringWithInt.loop
.endloop:
    ret

initVideoMem:
    cld
    mov cx, 0xB800
    mov es, cx
    mov di, 0x0000
    mov al, ' '
    mov ah, 0x07
    mov cx, 80*25
    rep stosw

    xor cx, cx
    mov es, cx
    mov word [es:BDA_NUM_COLS], 80
    mov word [es:BDA_NUM_ROWS], 25

    mov di, BDA_CURPOS_PAGES
    mov cx, 8
    xor ax, ax
    rep stosw

    ret

printNum:
    push dx
    push cx

    ; output to debug number port
    out 0xE9, ax
    ; initialize the counter with 0
    xor cx, cx
    ; current number length in cx
    ; number base in bx
    ; currently evaluated number in ax
.divisionLoop:
    ; clear the upper half of the divided number
    xor dx, dx
    ; divide ax by number base
    div bx
    ; increment the number of digits
    inc cx
    ; push the remainder of dividing by base to the stack
    push dx
    ; if there's no number to be divided next, end the division loop
    cmp ax, 0
    jz .endDivLoop
    ; loop
    jmp .divisionLoop
.endDivLoop:
    ; now, we have the digits (modulo by base) of our number on the stack
    ; first we divided by base, then base*base, then base*base*base etc.
    ; therefore the first one popped off the stack is the last digit
.printLoop:
    ; if no characters are left, jmp to end
    jcxz .endPrintLoop
    ; get the next number into ax
    pop ax
    ; convert the number to ASCII
    cmp al, 10
    jb .add0c
    add al, ('A' - 10)
    jmp .overAdd0c
.add0c:
    add al, '0'
.overAdd0c:
    stosb
    mov byte [es:di], 0
    ; decrement the number of characters left
    dec cx
    ; loop
    jmp .printLoop
.endPrintLoop:
    ; restore registers
    pop cx
    pop dx
    ret

callDecodeTable:
    push si
    push cx
    push bx
    push dx
    push es
    push ds
    push ss
    push bp

    push bx
    xor bh, bh

    mov bl, ah
    add di, bx
    add di, bx

    mov bx, cs
    mov ds, bx

    pop bx

    mov di, [di]
    call di

    pop bp
    pop ss
    pop ds
    pop es
    pop dx
    pop bx
    pop cx
    pop si
    ret

biosVideo:
    cmp ah, 0x0f ; Write character at cursor position
    jbe .do

    iret
.do:
    push di
    lea di, [.decodeTable]
    call callDecodeTable
    pop di
    iret
.decodeTable:
    dw unimplemented ; Set video mode
    dw unimplemented ; Set cursor type
    dw setCursorPosition ; Set cursor position
    dw getCursorPosition ; Read cursor position
    dw unimplemented ; Read light pen
    dw unimplemented ; Select active display page
    dw unimplemented ; Scroll active page up
    dw unimplemented ; Scroll active page down
    dw unimplemented ; Read character and attribute at cursor
    dw unimplemented ; Write character and attribute at cursor
    dw unimplemented ; Write character at current cursor
    dw unimplemented ; Set color palette
    dw unimplemented ; Write graphics pixel at coordinate
    dw unimplemented ; Read graphics pixel at coordinate
    dw writeTextTeletype ; Write text in teletype mode
    dw unimplemented ; Get current video state

setCursorPosition:
    push es
    mov di, BDA_CURPOS_PAGES
    push bx
    mov bl, bh
    xor bh, bh
    shl bx, 1
    add di, bx
    xor bx, bx
    mov es, bx
    mov [es:di], dx
    ; TODO: call videoDriverUpdateCursorPos
    pop bx
    pop es
    jmp ignoreGood

getCursorPosition:
    push es
    mov di, BDA_CURPOS_PAGES
    push bx
    mov bl, bh
    xor bh, bh
    shl bx, 1
    add di, bx
    xor bx, bx
    mov es, bx
    mov dx, [es:di]
    pop bx
    pop es

    jmp returnDx

scrollIntoRange:
    push es
    push ds
    push bx

    call loadCursorPosPtr

    ; Scroll the screen if necessary, TODO
    mov al, [es:BDA_NUM_ROWS]
    cmp byte [es:si + 1], al

    jnge .rowsInRange

    ; Store [BDA_NUM_ROWS] for later
    push ax

    ; Clamp the rows value and calculate the difference
    dec al
    xchg byte [es:si + 1], al
    sub al, byte [es:si + 1]

    ; Calculate the character delta
    mov bl, [es:BDA_NUM_COLS]
    mul bl

    ; Move delta to BX and [BDA_NUM_COLS] to AL
    xchg bx, ax

    ; Get [BDA_NUM_ROWS] from the stack
    xchg sp, bp
    mov ah, [bp]
    xchg sp, bp
    ; It got pushed as a whole register
    add sp, 2
    ; Get total characters into AX
    mul ah

    push cx
    mov cx, ax
    sub cx, bx
    push bx

    mov si, bx
    shl si, 1
    mov bx, 0xB800
    mov es, bx
    mov ds, bx
    xor di, di

    ; TODO: account for videomodes
    rep movsw
    pop bx
    mov cx, bx
    mov di, ax
    sub di, cx
    shl di, 1
    mov ax, 0x0700
    rep stosw

    pop cx
.rowsInRange:
    pop bx
    pop ds
    pop es
    ret

writeTextTeletype:
    cmp al, 0xA
    je .cr

    cmp al, 0xD
    je .lf

    cmp al, 0x08
    je .backspace

    push es
    push bx
    push si
    push ax

    xor bx, bx
    mov es, bx

    ; Push ax a second time
    push ax

    call scrollIntoRange

    call loadCursorPosPtr

    ; Set ax to BDA_NUM_COLS
    mov ax, [es:BDA_NUM_COLS]

    ; Get the cursor positions into bl and bh
    xor bx, bx
    mov es, bx
    mov bx, [es:si]

    ; Set bx to columns and di to rows
    ; xchg bh, bl
    mov di, bx
    and di, 0xFF
    xchg bh, bl
    xor bh, bh

    ; Set ax to character index by multiplying row index with BDA_NUM_COLS and
    ; adding column index
    imul bx
    add ax, di

    ; Set ax to character offset: TODO, videomodes
    shl ax, 1

    ; Store character offset result in di
    mov di, ax

    pop ax

    ; Load es with the text mode video memory segment
    mov bx, 0xB800
    mov es, bx

    ; Store character
    mov [es:di], al

    ; Restore es to the BDA segment
    xor ax, ax
    mov es, ax

    ; Store the number of columns in al
    ; NOTE: if you change this to ax, remember to change the "Zero the column
    ; index" line too.
    mov al, [es:BDA_NUM_COLS]

    ; Advance the cursor position
    inc byte [es:si]

    ; If the column index is out of range
    cmp byte [es:si], al
    jnge .columnsInRange
    ; Zero the column index
    mov byte [es:si], 0
    ; Increment the row index
    inc byte [es:si + 1]

.columnsInRange:

    pop ax
    pop si
    pop bx
    pop es

    jmp ignoreGood
.cr:
    push si
    push bx
    push es

    call loadCursorPosPtr

    xor bx, bx
    mov es, bx

    inc byte [es:si + 1]

    pop es

    pop bx
    pop si

    jmp ignoreGood
.lf:
    push si
    push bx
    push es

    call loadCursorPosPtr

    xor bx, bx
    mov es, bx

    mov byte [es:si], 0

    pop es
    pop bx
    pop si
    jmp ignoreGood
.backspace:
    push si
    push bx
    push es

    call loadCursorPosPtr

    xor bx, bx
    mov es, bx

    dec byte [es:si]
    jns .colOk
.colUnderflow:
    test byte [es:si + 1], 0xFF
    jnz .gotoPrevRow
.cantGotoNextRow:
    inc byte [es:si]
    jmp .colOk
.gotoPrevRow:
    mov bl, [es:BDA_NUM_COLS]
    dec bl
    mov [es:si], bl
    dec byte [es:si + 1]
.colOk:

    pop es
    pop bx
    pop si
    jmp ignoreGood

; Get the cursor position pointer into si
loadCursorPosPtr:
    mov si, BDA_CURPOS_PAGES
    mov bl, bh
    xor bh, bh
    shl bx, 1
    add si, bx
    ret

ignoreGood:
    add sp, 2
    pop bp
    pop ss
    pop ds
    pop es
    pop dx
    pop bx
    pop cx
    pop si
    add sp, 2
    pop di
    iret

returnSetZF:
    add sp, 2
    pop bp
    pop ss
    pop ds
    pop es
    pop dx
    pop bx
    pop cx
    pop si
    add sp, 2
    pop di
    cli
    xchg bp, sp
    or word [bp+4], 0x0040
    xchg bp, sp
    iret

returnClearZF:
    add sp, 2
    pop bp
    pop ss
    pop ds
    pop es
    pop dx
    pop bx
    pop cx
    pop si
    add sp, 2
    pop di
    cli
    xchg bp, sp
    and word [bp+4], ~0x0040
    xchg bp, sp
    iret

returnDx:
    add sp, 2
    pop bp
    pop ss
    pop ds
    pop es
    add sp, 2
    pop bx
    pop cx
    pop si
    add sp, 2
    pop di
    iret

biosEquipment:
    push es
    xor ax, ax
    mov es, ax
    mov ax, [es:BDA_EQUIPMENT]
    pop es
    iret

biosMemory:
    mov ax, 0x280 ; 640K conventional memory
    iret

biosMisc:
    stc
    iret

biosBlock:
    test dl, 0x80
    jnz .hddNotSupported

    cmp ah, 0x00 ; Reset disk
    je  unimplementedIret
    cmp ah, 0x01 ; Get last status
    je  unimplementedIret
    cmp ah, 0x02 ; Read disk
    je  readSectorsFromDrive
    cmp ah, 0x03 ; Write disk
    je  unimplementedIret
    cmp ah, 0x04 ; Verify disk
    je  unimplementedIret
    cmp ah, 0x05
    je  unimplementedIret
    cmp ah, 0x08 ; Get drive parameters
    je  getInt13Params
    cmp ah, 0x0c ; Seek
    je  unimplementedIret
    cmp ah, 0x10 ; Check if drive ready
    je  unimplementedIret
    cmp ah, 0x15
    jz int13h15h

    mov ah, 1
    jmp reachStackStc
.hddNotSupported:
    mov ah, 0x0F
    jmp reachStackStc

int13h15h:
    mov ah, 1
    jmp reachStackClc

getInt13Params:
    xor ax, ax
    mov dl, [es:BDA_HDDNUM]
    mov cl, [int1e + 4]
    mov ch, 79
    mov dh, 1
    xor bl, bl
    mov di, int1e
    push cs
    pop es
    jmp reachStackClc

isrTimer:
    push ax
    mov ax, 0x20
    out 0x20, ax
    pop ax
    iret

isrKeyboard:
    push es
    push ax
    push bx

    in al, 0x60

    mov ah, al
    and ah, 0x7F

    xor bx, bx
    mov es, bx

    cmp al, KBD_SCANCODE_EXT
    je .extendedScancode

    cmp ah, KBD_SCANCODE_LSHIFT
    je .leftShift
    cmp ah, KBD_SCANCODE_RSHIFT
    je .rightShift
    cmp ah, KBD_SCANCODE_CTRL
    je .leftCtrl
    cmp ah, KBD_SCANCODE_ALT
    je .leftAlt
    cmp ah, KBD_SCANCODE_CPSLCK
    je .capsLock

    test al, 0x80 ; Key up?
    jnz .dontAddToBuff

    mov ah, [es:BDA_KBD_SHIFT_STATUS1]

    test ah, 0x40
    jz .noCaps
    add bx, scancodeCapsToAscii - scancodeNormalToAscii
.noCaps:
    test ah, 0x3
    jz .noShift
    add bx, scancodeShiftToAscii - scancodeNormalToAscii
.noShift:
    mov ah, al
    add bx, scancodeNormalToAscii
    cs xlat

.noTranslate:
    mov bx, [es:BDA_KBDTAIL]
    mov word [es:bx + 0x400], ax
    add word [es:BDA_KBDTAIL], 2

    call keyboardAdjustBuffer

.dontAddToBuff:
    mov ax, 1
    out 0x64, ax

    mov ax, 0x20
    out 0x20, ax

    pop bx
    pop ax
    pop es

    iret
.leftShift:
    mov ah, al
    and byte [es:BDA_KBD_SHIFT_STATUS1], ~2
    test al, 0x80
    jnz .clearAl
    or byte [es:BDA_KBD_SHIFT_STATUS1], 2
    jmp .clearAl
.rightShift:
    mov ah, al
    and byte [es:BDA_KBD_SHIFT_STATUS1], ~1
    test al, 0x80
    jnz .clearAl
    or byte [es:BDA_KBD_SHIFT_STATUS1], 1
    jmp .clearAl
.leftAlt:
    mov ah, al
    ; Clear the LALT flag
    and byte [es:BDA_KBD_SHIFT_STATUS2], ~2
    test al, 0x80
    jnz .updateAlt
    ; Set the LALT flag
    or byte [es:BDA_KBD_SHIFT_STATUS2], 2
    jmp .updateAlt
.leftCtrl:
    mov ah, al
    ; Clear the LCTRL flag
    and byte [es:BDA_KBD_SHIFT_STATUS2], ~1
    test al, 0x80
    jnz .updateCtrl
    ; Set the LCTRL flag
    or byte [es:BDA_KBD_SHIFT_STATUS2], 1
    jmp .updateCtrl
.capsLock:
    test al, 0x80
    jnz .capsReleased

    ; Detect the rising edge of the keypress
    test byte [es:BDA_KBD_SHIFT_STATUS2], 0x40
    jnz .capsAlreadyPressed

    ; Toggle CAPSLOCK active
    xor byte [es:BDA_KBD_SHIFT_STATUS1], 0x40
.capsAlreadyPressed:
    ; Set the CAPSLOCK pressed flag
    or byte [es:BDA_KBD_SHIFT_STATUS2], 0x40
    jmp .clearAl
.capsReleased:
    ; Clear the CAPSLOCK pressed flag
    and byte [es:BDA_KBD_SHIFT_STATUS2], ~0x40
    jmp .clearAl
.extendedScancode:
    jmp .clearAl
.clearAl:
    xor al, al
    jmp .noTranslate
.updateCtrl:
    ; Clear the CTRL flag
    and byte [es:BDA_KBD_SHIFT_STATUS1], ~4
    ; Check the LCTRL and RCTRL flags
    test byte [es:BDA_KBD_SHIFT_STATUS2], 1 | 4
    jz .clearAl
    or byte [es:BDA_KBD_SHIFT_STATUS1], 4
    jmp .clearAl
.updateAlt:
    ; Clear the ALT flag
    and byte [es:BDA_KBD_SHIFT_STATUS1], ~8
    ; Check the LALT and RALT flags
    test byte [es:BDA_KBD_SHIFT_STATUS2], 2 | 8
    jz .clearAl
    ; Set the ALT flag
    or byte [es:BDA_KBD_SHIFT_STATUS1], 8
    jmp .clearAl


keyboardAdjustBuffer:
    push es
    push ax
    push bx

    mov ax, 0x40
    mov es, ax

    cli

    mov ax, WORD [es:BDA_KBDHEAD - 0x400]
    out 0xe9, ax

    mov ax, WORD [es:BDA_KBDTAIL - 0x400]
    out 0xe9, ax

    cmp WORD [es:BDA_KBDHEAD - 0x400], BDA_KBDBUF - 0x400 + 32
    jng .headInRange
    mov WORD [es:BDA_KBDHEAD - 0x400], BDA_KBDBUF - 0x400
.headInRange:
    cmp WORD [es:BDA_KBDTAIL - 0x400], BDA_KBDBUF - 0x400 + 32
    jb .tailInRange
    mov WORD [es:BDA_KBDTAIL - 0x400], BDA_KBDBUF - 0x400
.tailInRange:
    sti

    pop bx
    pop ax
    pop es
    ret

checkKey:
    push bx
    push cx
    push es

    mov bx, 0x40
    mov es, bx
    cli

    mov cx, [es:BDA_KBDTAIL - 0x400]
    mov bx, [es:BDA_KBDHEAD - 0x400]
    mov ax, [es:bx]

    cmp cx, bx
    pop es
    pop cx
    pop bx
    jnz .clearZ
    xchg bp, sp
    or word [bp + 4], 0x40
    xchg bp, sp
    iret
.clearZ:
    xchg bp, sp
    and word [bp + 4], ~0x40
    xchg bp, sp
    iret

getKey:
    sti
    push es

    mov ax, 0x40
    mov es, ax

.waitLoop:
    mov bx, [es:BDA_KBDHEAD - 0x400]
    cmp bx, [es:BDA_KBDTAIL - 0x400]
    jz .waitLoop

    mov ax, [es:bx]
    add word [es:BDA_KBDHEAD - 0x400], 2
    call keyboardAdjustBuffer
    push ax
    mov ax, [es:BDA_KBDHEAD - 0x400]
    out 0xe9, ax
    pop ax
    pop es
    iret

int1a:
    xor ax, ax
    xor bx, bx
    xor cx, cx
    xor dx, dx
    iret

int1e:
    db 0xdf
    db 0x02
    db 0x25
    db 0x02
    db 18
    db 0x1B
    db 0xFF
    db 0x54
    db 0xF6
    db 0x0F
    db 0x08

reachStackStc:
    cli
    xchg bp, sp
    or word [bp+4], 1
    xchg bp, sp
    iret

reachStackClc:
    cli
    xchg bp, sp
    and word [bp+4], 0xfffe
    xchg bp, sp
    iret

reachStackCarry:
    jc reachStackStc
    jmp reachStackClc

initIvt:
    xor di, di
.initIvtLoop:
    mov word [es:di], biosMisc
    mov word [es:di + 2], cs

    add di, 4
    cmp di, 1024
    jl .initIvtLoop

    mov word [es:(03h * 4)],     int3h
    mov word [es:(03h * 4) + 2], cs

    mov word [es:(08h * 4)],     isrTimer
    mov word [es:(08h * 4) + 2], cs

    mov word [es:(09h * 4)],     isrKeyboard
    mov word [es:(09h * 4) + 2], cs

    mov word [es:(10h * 4)    ], biosVideo
    mov word [es:(10h * 4) + 2], cs

    mov word [es:(11h * 4)],     biosEquipment
    mov word [es:(11h * 4) + 2], cs

    mov word [es:(12h * 4)],     biosMemory
    mov word [es:(12h * 4) + 2], cs

    mov word [es:(13h * 4)],     biosBlock
    mov word [es:(13h * 4) + 2], cs

    mov word [es:(16h * 4)],     biosKeyboard
    mov word [es:(16h * 4) + 2], cs

    mov word [es:(1Ah * 4)],     int1a
    mov word [es:(1Ah * 4) + 2], cs

    mov word [es:(1Eh * 4)],     int1e
    mov word [es:(1Eh * 4) + 2], cs

    ret

biosBannerStr: db "NNX OC86BIOS, alpha v0.1.1.0, ",
testSuite: db "Debug/test build",0xa,0xd,0
testFailed: db "Test failed: ",0
testsSuccess: db "Tests passed",0xa,0xd,0
noPrimaryIde: db "No primary IDE",0xa,0xd,0
noBootableDevice: db "No bootable device",0xa,0xd,0
primaryIdeSizeStr: db "Primary IDE size: ",0
strUnitKb: db "KB",0xa,0xd,0

scancodeNormalToAscii:
.start:
db 0 ; null
db 0 ; esc
db "1234567890-="
db 8 ; backspace
db 0 ; tab
db "qwertyuiop[]"
db 0xD ; enter
db 0 ; left ctrl
db "asdfghjkl;'`"
db 0 ; left shift
db 0x5C ; backslash
db "zxcvbnm,./"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.end - .start) db 0
scancodeCapsToAscii:
.start:
db 0 ; null
db 0 ; esc
db "1234567890-="
db 8 ; backspace
db 0 ; tab
db "QWERTYUIOP[]"
db 0xD ; enter
db 0 ; left ctrl
db "ASDFGHJKL;'`"
db 0 ; left shift
db 0x5C ; backslash
db "ZXCVBNM,./"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.end - .start) db 0
scancodeShiftToAscii:
.start:
db 0 ; null
db 0 ; esc
db "!@#$%^&*()_+"
db 8 ; backspace
db 0 ; tab
db "QWERTYUIOP{}"
db 0xD ; enter
db 0 ; left ctrl
db 'ASDFGHJKL:"~'
db 0 ; left shift
db "|ZXCVBNM<>?"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.end - .start) db 0
scancodeCapsShiftToAscii:
.start:
db 0 ; null
db 0 ; esc
db "!@#$%^&*()_+"
db 8 ; backspace
db 0 ; tab
db "qwertyuiop{}"
db 0xD ; enter
db 0 ; left ctrl
db 'asdfghjkl:"~'
db 0 ; left shift
db "|zxcvbnm<>?"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.end - .start) db 0

biosKeyboard:
    cmp ah, 0x00
    je getKey
    cmp ah, 0x01
    je checkKey
    cmp ah, 0x02
    je getShiftStatus
    cmp ah, 0x12
    je getShiftStatus2
    iret

getShiftStatus:
    push es
    push bx

    xor bx, bx
    mov es, bx

    mov ah, [es:BDA_KBD_SHIFT_STATUS1]

    pop bx
    pop es
    iret

getShiftStatus2:
    push es

    xor ax, ax
    mov es, ax

    mov ax, [es:BDA_KBD_SHIFT_STATUS1]

    pop es
    iret

times (0xFFF0) - ($ - $$) hlt
jmp 0xF000:0x0000
times (0x10000) - ($ - $$) hlt