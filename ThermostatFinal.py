"""
Thermostat - Professional IoT Climate Control System

This is a production-ready Python implementation of a thermostat system
that demonstrates the Observer Pattern for IoT device management.

The system monitors temperature and humidity, manages heating/cooling states,
and provides real-time visual feedback through LEDs and an LCD display.

Functionality:
    - Three operational states: OFF, HEAT, COOL
    - Visual LED indicators with pulsing effects when actively heating/cooling
    - Real-time LCD display with temperature and state information
    - Button controls for state cycling and setpoint adjustment
    - Serial communication for remote monitoring
    - Observer Pattern for decoupled component updates

Hardware Components:
    - AHTx0 Temperature/Humidity Sensor (I2C)
    - 16x2 Character LCD Display
    - Red and Blue PWM LEDs
    - Three push buttons (state cycle, increment, decrement)
    - Serial UART connection for server updates

Change History:
---------------------------------------------------------------------------
Version | Date       | Author           | Description
---------------------------------------------------------------------------
1.0     | [Original] | Original Author  | Initial procedural development
2.0     | 2026-01-24 | Refactoring Team | Observer Pattern refactoring
        |            |                  | - Implemented Subject/Observer
        |            |                  | - Decoupled GPIO configuration
        |            |                  | - Added defensive error handling
        |            |                  | - Enhanced thread safety
        |            |                  | - PEP 8 compliance
---------------------------------------------------------------------------
"""

import logging
from abc import ABC, abstractmethod
from datetime import datetime
from math import floor
from threading import Thread, Lock
from time import sleep
from typing import List, Dict, Optional, Any

import board
import digitalio
import serial
import adafruit_ahtx0
import adafruit_character_lcd.character_lcd as characterlcd
from gpiozero import Button, PWMLED
from statemachine import StateMachine, State


# =============================================================================
# CONFIGURATION
# =============================================================================

# Hardware Configuration Dictionary
HARDWARE_CONFIG: Dict[str, Any] = {
    # LCD Display GPIO Pins
    'lcd_pins': {
        'rs': 'D17',
        'en': 'D27',
        'd4': 'D5',
        'd5': 'D6',
        'd6': 'D13',
        'd7': 'D26',
    },
    # LED GPIO Pins
    'led_pins': {
        'red': 18,
        'blue': 23,
    },
    # Button GPIO Pins
    'button_pins': {
        'state_cycle': 24,
        'increment': 25,
        'decrement': 12,
    },
    # Serial Configuration
    'serial': {
        'port': '/dev/ttyS0',
        'baudrate': 115200,
        'timeout': 1,
    },
    # LCD Display Configuration
    'lcd_display': {
        'columns': 16,
        'rows': 2,
    },
    # Operational Parameters
    'timing': {
        'display_update_interval': 1,  # seconds
        'server_update_interval': 30,  # seconds
        'main_loop_sleep': 30,  # seconds
    },
}

# Application Configuration
DEFAULT_SETPOINT: int = 72  # degrees Fahrenheit
DEBUG: bool = True

# Configure logging
logging.basicConfig(
    level=logging.INFO if DEBUG else logging.WARNING,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


# =============================================================================
# OBSERVER PATTERN IMPLEMENTATION
# =============================================================================

class Observer(ABC):
    """
    Abstract base class defining the Observer interface.

    All concrete observers must implement the update() method to respond
    to notifications from Subject instances.
    """

    @abstractmethod
    def update(self, subject: 'Subject') -> None:
        """
        Receive update notification from subject.

        Args:
            subject: The subject that triggered the update
        """
        pass


class Subject:
    """
    Base class for objects that can be observed.

    Maintains a list of observers and notifies them of state changes.
    Thread-safe implementation using locks for observer list management.
    """

    def __init__(self) -> None:
        """Initialize the subject with an empty observer list."""
        self._observers: List[Observer] = []
        self._lock: Lock = Lock()

    def attach(self, observer: Observer) -> None:
        """
        Attach an observer to this subject.

        Args:
            observer: The observer to attach
        """
        with self._lock:
            if observer not in self._observers:
                self._observers.append(observer)
                logger.info(f"Attached observer: {observer.__class__.__name__}")

    def detach(self, observer: Observer) -> None:
        """
        Detach an observer from this subject.

        Args:
            observer: The observer to detach
        """
        with self._lock:
            if observer in self._observers:
                self._observers.remove(observer)
                logger.info(f"Detached observer: {observer.__class__.__name__}")

    def notify(self) -> None:
        """Notify all attached observers of a state change."""
        with self._lock:
            observers_copy = self._observers.copy()

        for observer in observers_copy:
            try:
                observer.update(self)
            except Exception as e:
                logger.error(
                    f"Error notifying observer {observer.__class__.__name__}: {e}",
                    exc_info=True
                )


# =============================================================================
# CLIMATE SENSOR (SUBJECT)
# =============================================================================

class ClimateSensor(Subject):
    """
    Climate sensor that monitors temperature and humidity.

    Acts as a Subject in the Observer Pattern, notifying observers
    when sensor readings are updated. Implements defensive error handling
    for I2C communication failures.
    """

    def __init__(self, i2c_bus: board.I2C) -> None:
        """
        Initialize the climate sensor.

        Args:
            i2c_bus: I2C bus instance for sensor communication
        """
        super().__init__()
        self._sensor: Optional[adafruit_ahtx0.AHTx0] = None
        self._temperature_c: float = 0.0
        self._humidity: float = 0.0
        self._last_read_successful: bool = False
        self._lock: Lock = Lock()

        try:
            self._sensor = adafruit_ahtx0.AHTx0(i2c_bus)
            logger.info("Climate sensor initialized successfully")
        except Exception as e:
            logger.error(f"Failed to initialize climate sensor: {e}", exc_info=True)

    def read_sensor(self) -> bool:
        """
        Read current temperature and humidity from the sensor.

        Returns:
            bool: True if read was successful, False otherwise
        """
        if self._sensor is None:
            logger.warning("Sensor not initialized, cannot read")
            return False

        try:
            with self._lock:
                self._temperature_c = self._sensor.temperature
                self._humidity = self._sensor.humidity
                self._last_read_successful = True

            logger.debug(
                f"Sensor read successful: {self._temperature_c:.1f}°C, "
                f"{self._humidity:.1f}%"
            )
            self.notify()
            return True

        except Exception as e:
            logger.error(f"Failed to read from climate sensor: {e}", exc_info=True)
            with self._lock:
                self._last_read_successful = False
            return False

    @property
    def temperature_fahrenheit(self) -> float:
        """
        Get temperature in Fahrenheit.

        Returns:
            float: Temperature in degrees Fahrenheit
        """
        with self._lock:
            return (self._temperature_c * 9.0 / 5.0) + 32.0

    @property
    def temperature_celsius(self) -> float:
        """
        Get temperature in Celsius.

        Returns:
            float: Temperature in degrees Celsius
        """
        with self._lock:
            return self._temperature_c

    @property
    def humidity(self) -> float:
        """
        Get relative humidity.

        Returns:
            float: Relative humidity percentage
        """
        with self._lock:
            return self._humidity

    @property
    def is_healthy(self) -> bool:
        """
        Check if sensor is functioning properly.

        Returns:
            bool: True if last read was successful
        """
        with self._lock:
            return self._last_read_successful


# =============================================================================
# THERMOSTAT STATE MACHINE
# =============================================================================

class ThermostatStateMachine(StateMachine):
    """
    State machine for managing thermostat operational states.

    States:
        - OFF: No heating or cooling
        - HEAT: Heating mode active
        - COOL: Cooling mode active

    The state machine acts as a Subject, notifying observers when
    state transitions occur or setpoint changes.
    """

    # Define states
    off = State(initial=True)
    heat = State()
    cool = State()

    # Define state transitions
    cycle = (
        off.to(heat) |
        heat.to(cool) |
        cool.to(off)
    )

    def __init__(self, climate_sensor: ClimateSensor,
                 initial_setpoint: int = DEFAULT_SETPOINT) -> None:
        """
        Initialize the thermostat state machine.

        Args:
            climate_sensor: Climate sensor for temperature readings
            initial_setpoint: Initial temperature setpoint in Fahrenheit
        """
        super().__init__()
        self._climate_sensor: ClimateSensor = climate_sensor
        self._setpoint: int = initial_setpoint
        self._lock: Lock = Lock()
        self._observers: List[Observer] = []

        logger.info(f"Thermostat initialized with setpoint: {self._setpoint}°F")

    @property
    def setpoint(self) -> int:
        """Get current temperature setpoint."""
        with self._lock:
            return self._setpoint

    @setpoint.setter
    def setpoint(self, value: int) -> None:
        """
        Set temperature setpoint and notify observers.

        Args:
            value: New setpoint in degrees Fahrenheit
        """
        with self._lock:
            self._setpoint = value
        logger.info(f"Setpoint changed to: {self._setpoint}°F")
        self._notify_observers()

    @property
    def climate_sensor(self) -> ClimateSensor:
        """Get the associated climate sensor."""
        return self._climate_sensor

    @property
    def current_temperature(self) -> float:
        """Get current temperature in Fahrenheit."""
        return self._climate_sensor.temperature_fahrenheit

    def attach_observer(self, observer: Observer) -> None:
        """
        Attach an observer to receive state change notifications.

        Args:
            observer: Observer to attach
        """
        if observer not in self._observers:
            self._observers.append(observer)
            logger.info(f"Attached observer to thermostat: {observer.__class__.__name__}")

    def _notify_observers(self) -> None:
        """Notify all attached observers of state changes."""
        for observer in self._observers:
            try:
                observer.update(self)
            except Exception as e:
                logger.error(
                    f"Error notifying observer {observer.__class__.__name__}: {e}",
                    exc_info=True
                )

    def increment_setpoint(self) -> None:
        """Increase setpoint by one degree."""
        self.setpoint = self._setpoint + 1

    def decrement_setpoint(self) -> None:
        """Decrease setpoint by one degree."""
        self.setpoint = self._setpoint - 1

    def cycle_state(self) -> None:
        """Cycle through thermostat states."""
        logger.info(f"Cycling state from: {self.current_state.id}")
        self.cycle()

    # State transition handlers
    def on_enter_heat(self) -> None:
        """Handler for entering HEAT state."""
        logger.info("Entering HEAT mode")
        self._notify_observers()

    def on_exit_heat(self) -> None:
        """Handler for exiting HEAT state."""
        logger.debug("Exiting HEAT mode")
        self._notify_observers()

    def on_enter_cool(self) -> None:
        """Handler for entering COOL state."""
        logger.info("Entering COOL mode")
        self._notify_observers()

    def on_exit_cool(self) -> None:
        """Handler for exiting COOL state."""
        logger.debug("Exiting COOL mode")
        self._notify_observers()

    def on_enter_off(self) -> None:
        """Handler for entering OFF state."""
        logger.info("Entering OFF mode")
        self._notify_observers()


# =============================================================================
# LED CONTROLLER OBSERVER
# =============================================================================

class LEDController(Observer):
    """
    Observer that controls LED indicators based on thermostat state.

    Controls red and blue PWM LEDs to provide visual feedback:
        - OFF state: Both LEDs off
        - HEAT state: Red LED pulsing (if temp < setpoint) or solid
        - COOL state: Blue LED pulsing (if temp > setpoint) or solid
    """

    def __init__(self, config: Dict[str, int]) -> None:
        """
        Initialize LED controller.

        Args:
            config: Dictionary containing 'red' and 'blue' GPIO pin numbers
        """
        self._red_led: Optional[PWMLED] = None
        self._blue_led: Optional[PWMLED] = None
        self._lock: Lock = Lock()

        try:
            self._red_led = PWMLED(config['red'])
            self._blue_led = PWMLED(config['blue'])
            logger.info(
                f"LED Controller initialized - Red: GPIO{config['red']}, "
                f"Blue: GPIO{config['blue']}"
            )
        except Exception as e:
            logger.error(f"Failed to initialize LED controller: {e}", exc_info=True)

    def update(self, subject: Any) -> None:
        """
        Update LED display based on thermostat state.

        Args:
            subject: ThermostatStateMachine instance
        """
        if not isinstance(subject, ThermostatStateMachine):
            return

        if self._red_led is None or self._blue_led is None:
            logger.warning("LEDs not initialized, cannot update")
            return

        try:
            with self._lock:
                self._update_leds(subject)
        except Exception as e:
            logger.error(f"Error updating LEDs: {e}", exc_info=True)

    def _update_leds(self, thermostat: ThermostatStateMachine) -> None:
        """
        Internal method to update LED states.

        Args:
            thermostat: Thermostat state machine
        """
        current_temp = floor(thermostat.current_temperature)
        setpoint = thermostat.setpoint
        state = thermostat.current_state.id

        # Turn off both LEDs first
        self._red_led.off()
        self._blue_led.off()

        logger.debug(
            f"Updating LEDs - State: {state}, Temp: {current_temp}°F, "
            f"Setpoint: {setpoint}°F"
        )

        if state == "heat":
            if current_temp < setpoint:
                self._red_led.pulse()
                logger.debug("Red LED pulsing (heating)")
            else:
                self._red_led.value = 1
                logger.debug("Red LED solid (at temperature)")

        elif state == "cool":
            if current_temp > setpoint:
                self._blue_led.pulse()
                logger.debug("Blue LED pulsing (cooling)")
            else:
                self._blue_led.value = 1
                logger.debug("Blue LED solid (at temperature)")

    def cleanup(self) -> None:
        """Cleanup LED resources."""
        try:
            if self._red_led:
                self._red_led.off()
                self._red_led.close()
            if self._blue_led:
                self._blue_led.off()
                self._blue_led.close()
            logger.info("LED controller cleanup complete")
        except Exception as e:
            logger.error(f"Error during LED cleanup: {e}", exc_info=True)


# =============================================================================
# LCD DISPLAY OBSERVER
# =============================================================================

class LCDDisplay(Observer):
    """
    Observer that manages the 16x2 LCD display.

    Runs in a separate thread to continuously update the display with:
        - Line 1: Current date and time
        - Line 2: Alternates between current temperature and thermostat state/setpoint

    Also handles periodic serial communication for remote monitoring.
    """

    def __init__(self, config: Dict[str, Any], serial_conn: Optional[serial.Serial]) -> None:
        """
        Initialize LCD display controller.

        Args:
            config: Hardware configuration dictionary
            serial_conn: Serial connection for server updates (optional)
        """
        self._lcd: Optional[characterlcd.Character_LCD_Mono] = None
        self._serial: Optional[serial.Serial] = serial_conn
        self._running: bool = False
        self._thread: Optional[Thread] = None
        self._thermostat: Optional[ThermostatStateMachine] = None
        self._lock: Lock = Lock()

        # Display configuration
        self._columns: int = config['lcd_display']['columns']
        self._rows: int = config['lcd_display']['rows']

        # Initialize LCD hardware
        try:
            pins = config['lcd_pins']
            lcd_rs = digitalio.DigitalInOut(getattr(board, pins['rs']))
            lcd_en = digitalio.DigitalInOut(getattr(board, pins['en']))
            lcd_d4 = digitalio.DigitalInOut(getattr(board, pins['d4']))
            lcd_d5 = digitalio.DigitalInOut(getattr(board, pins['d5']))
            lcd_d6 = digitalio.DigitalInOut(getattr(board, pins['d6']))
            lcd_d7 = digitalio.DigitalInOut(getattr(board, pins['d7']))

            self._lcd = characterlcd.Character_LCD_Mono(
                lcd_rs, lcd_en, lcd_d4, lcd_d5, lcd_d6, lcd_d7,
                self._columns, self._rows
            )
            self._lcd.clear()

            # Store pin references for cleanup
            self._pins = {
                'rs': lcd_rs, 'en': lcd_en, 'd4': lcd_d4,
                'd5': lcd_d5, 'd6': lcd_d6, 'd7': lcd_d7
            }

            logger.info("LCD Display initialized successfully")
        except Exception as e:
            logger.error(f"Failed to initialize LCD display: {e}", exc_info=True)

    def update(self, subject: Any) -> None:
        """
        Receive update notifications.

        Args:
            subject: ThermostatStateMachine instance
        """
        if isinstance(subject, ThermostatStateMachine):
            with self._lock:
                self._thermostat = subject

    def start(self, thermostat: ThermostatStateMachine) -> None:
        """
        Start the display update thread.

        Args:
            thermostat: Thermostat state machine to monitor
        """
        with self._lock:
            self._thermostat = thermostat
            self._running = True

        self._thread = Thread(target=self._display_loop, daemon=True)
        self._thread.start()
        logger.info("LCD display thread started")

    def stop(self) -> None:
        """Stop the display update thread."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=2)
        logger.info("LCD display thread stopped")

    def _display_loop(self) -> None:
        """Main display update loop running in separate thread."""
        counter = 1
        alt_counter = 1

        while self._running:
            try:
                self._update_display(alt_counter)

                # Handle serial updates
                if (counter % HARDWARE_CONFIG['timing']['server_update_interval']) == 0:
                    self._send_serial_update()
                    counter = 1
                else:
                    counter += 1

                # Update alt_counter for display alternation
                alt_counter += 1
                if alt_counter > 10:
                    alt_counter = 1

                sleep(HARDWARE_CONFIG['timing']['display_update_interval'])

            except Exception as e:
                logger.error(f"Error in display loop: {e}", exc_info=True)
                sleep(1)

    def _update_display(self, alt_counter: int) -> None:
        """
        Update the LCD display content.

        Args:
            alt_counter: Counter for alternating display content
        """
        if self._lcd is None or self._thermostat is None:
            return

        try:
            # Line 1: Current date and time
            current_time = datetime.now()
            line1 = current_time.strftime("%b %d %H:%M:%S\n")

            # Line 2: Alternate between temperature and state
            if alt_counter < 6:
                temp = floor(self._thermostat.current_temperature)
                line2 = f"T:{temp}F"
            else:
                state = self._thermostat.current_state.id.upper()
                setpoint = self._thermostat.setpoint
                line2 = f"{state} {setpoint}F"

            # Update display
            self._lcd.clear()
            self._lcd.message = line1 + line2

            logger.debug(f"Display updated: {line1.strip()} | {line2}")

        except Exception as e:
            logger.error(f"Error updating LCD display: {e}", exc_info=True)

    def _send_serial_update(self) -> None:
        """Send status update over serial connection."""
        if self._serial is None or self._thermostat is None:
            return

        try:
            state = self._thermostat.current_state.id
            temp = self._thermostat.current_temperature
            setpoint = self._thermostat.setpoint

            message = f"{state},{temp:.1f},{setpoint}\n"
            self._serial.write(message.encode())

            logger.debug(f"Serial update sent: {message.strip()}")

        except Exception as e:
            logger.error(f"Failed to send serial update: {e}", exc_info=True)

    def cleanup(self) -> None:
        """Cleanup display resources."""
        self.stop()

        try:
            if self._lcd:
                self._lcd.clear()

            # Cleanup digital IO pins
            for pin_name, pin in self._pins.items():
                try:
                    pin.deinit()
                except Exception as e:
                    logger.warning(f"Error deinitializing pin {pin_name}: {e}")

            logger.info("LCD display cleanup complete")

        except Exception as e:
            logger.error(f"Error during LCD cleanup: {e}", exc_info=True)


# =============================================================================
# MAIN APPLICATION
# =============================================================================

class ThermostatApplication:
    """
    Main application class orchestrating the thermostat system.

    Initializes all hardware components, sets up the Observer Pattern
    relationships, and manages the application lifecycle.
    """

    def __init__(self, config: Dict[str, Any]) -> None:
        """
        Initialize the thermostat application.

        Args:
            config: Hardware configuration dictionary
        """
        self.config = config
        self._running: bool = False

        # Initialize hardware components
        self._init_hardware()

        # Create core system components
        self._climate_sensor = ClimateSensor(self._i2c)
        self._thermostat = ThermostatStateMachine(
            self._climate_sensor,
            DEFAULT_SETPOINT
        )

        # Create observers
        self._led_controller = LEDController(config['led_pins'])
        self._lcd_display = LCDDisplay(config, self._serial)

        # Attach observers
        self._thermostat.attach_observer(self._led_controller)
        self._thermostat.attach_observer(self._lcd_display)

        # Setup buttons
        self._setup_buttons()

        logger.info("Thermostat application initialized successfully")

    def _init_hardware(self) -> None:
        """Initialize hardware interfaces."""
        try:
            # Initialize I2C bus
            self._i2c = board.I2C()
            logger.info("I2C bus initialized")

            # Initialize serial connection
            serial_config = self.config['serial']
            self._serial = serial.Serial(
                port=serial_config['port'],
                baudrate=serial_config['baudrate'],
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE,
                bytesize=serial.EIGHTBITS,
                timeout=serial_config['timeout']
            )
            logger.info(f"Serial port initialized: {serial_config['port']}")

        except Exception as e:
            logger.error(f"Hardware initialization error: {e}", exc_info=True)
            self._serial = None

    def _setup_buttons(self) -> None:
        """Configure button event handlers."""
        try:
            button_config = self.config['button_pins']

            # State cycle button
            self._state_button = Button(button_config['state_cycle'])
            self._state_button.when_pressed = self._thermostat.cycle_state

            # Increment setpoint button
            self._inc_button = Button(button_config['increment'])
            self._inc_button.when_pressed = self._thermostat.increment_setpoint

            # Decrement setpoint button
            self._dec_button = Button(button_config['decrement'])
            self._dec_button.when_pressed = self._thermostat.decrement_setpoint

            logger.info("Button controls configured")

        except Exception as e:
            logger.error(f"Button setup error: {e}", exc_info=True)

    def run(self) -> None:
        """Start the thermostat application."""
        self._running = True

        # Start LCD display thread
        self._lcd_display.start(self._thermostat)

        # Initial sensor read and LED update
        self._climate_sensor.read_sensor()
        self._led_controller.update(self._thermostat)

        logger.info("Thermostat application running")
        logger.info(f"Initial setpoint: {self._thermostat.setpoint}°F")

        try:
            while self._running:
                # Periodic sensor reading
                self._climate_sensor.read_sensor()

                # Update LED display based on current conditions
                self._led_controller.update(self._thermostat)

                sleep(self.config['timing']['main_loop_sleep'])

        except KeyboardInterrupt:
            logger.info("Keyboard interrupt received")
            self.shutdown()

    def shutdown(self) -> None:
        """Gracefully shutdown the application."""
        logger.info("Shutting down thermostat application...")
        self._running = False

        # Cleanup observers
        self._lcd_display.cleanup()
        self._led_controller.cleanup()

        # Close serial connection
        if self._serial and self._serial.is_open:
            try:
                self._serial.close()
                logger.info("Serial connection closed")
            except Exception as e:
                logger.error(f"Error closing serial connection: {e}")

        logger.info("Shutdown complete")


# =============================================================================
# ENTRY POINT
# =============================================================================

def main() -> None:
    """Application entry point."""
    logger.info("=" * 70)
    logger.info("THERMOSTAT SYSTEM - Production Version 2.0")
    logger.info("=" * 70)

    try:
        app = ThermostatApplication(HARDWARE_CONFIG)
        app.run()
    except Exception as e:
        logger.critical(f"Fatal error: {e}", exc_info=True)
        return 1

    return 0


if __name__ == "__main__":
    exit(main())
